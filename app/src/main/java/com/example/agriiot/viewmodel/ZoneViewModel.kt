package com.example.agriiot.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.agriiot.data.local.ScheduleDao
import com.example.agriiot.data.local.ScheduleEntity
import com.example.agriiot.data.local.SharedPrefsHelper
import com.example.agriiot.data.model.EventItem
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.model.ZoneState
import com.example.agriiot.data.repository.ZoneRepository
import com.example.agriiot.util.NotificationHelper
import com.example.agriiot.worker.DeviceControlWorker
import com.example.agriiot.worker.ScheduleWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: ZoneState) : UiState()
    data class Error(val message: String) : UiState()
}

@HiltViewModel
class ZoneViewModel @Inject constructor(
    application: Application,
    private val repository: ZoneRepository,
    private val workManager: WorkManager,
    private val sharedPrefsHelper: SharedPrefsHelper,
    private val scheduleDao: ScheduleDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    private val _criticalEvent = MutableSharedFlow<EventItem>()
    val criticalEvent: SharedFlow<EventItem> = _criticalEvent.asSharedFlow()

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading.asStateFlow()

    private val _availableZones = MutableStateFlow(listOf("zone-01", "zone-02", "zone-03"))
    val availableZones: StateFlow<List<String>> = _availableZones.asStateFlow()

    private val _selectedZoneId = MutableStateFlow("zone-01")
    val selectedZoneId: StateFlow<String> = _selectedZoneId.asStateFlow()

    val localSchedules: StateFlow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
        viewModelScope.launch {
            _selectedZoneId.collect {
                refresh()
            }
        }
        startPolling()
    }

    fun selectZone(newZoneId: String) {
        _selectedZoneId.value = newZoneId
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchData()
                fetchEvents()
                delay(5000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            fetchData()
            fetchEvents()
        }
    }

    private suspend fun fetchData() {
        repository.getZoneState(_selectedZoneId.value).onSuccess {
            _uiState.value = UiState.Success(it)
        }.onFailure {
            _uiState.value = UiState.Error(it.message ?: "Unknown error")
        }
    }

    private suspend fun fetchEvents() {
        repository.getZoneEvents(_selectedZoneId.value, limit = 20).onSuccess { response ->
            val newEvents = response.events
            _events.value = newEvents
            
            val lastSeenTimestamp = sharedPrefsHelper.getLastSeenTimestamp()
            
            newEvents.filter { it.timestamp > lastSeenTimestamp }.forEach { event ->
                if (lastSeenTimestamp.isNotEmpty()) {
                    if (event.severity == "critical" || event.eventType == "water_tank_low") {
                        _criticalEvent.emit(event)
                        NotificationHelper.showNotification(
                            getApplication(),
                            "Alert [${event.zoneId}]: ${event.eventType.replace("_", " ").uppercase()}",
                            event.message
                        )
                    }
                }
            }
            
            newEvents.maxByOrNull { it.timestamp }?.let {
                sharedPrefsHelper.setLastSeenTimestamp(it.timestamp)
            }
        }
    }

    fun sendCommand(target: String, action: String) {
        if (_isManualLoading.value) return
        
        viewModelScope.launch {
            _isManualLoading.value = true
            repository.sendCommand(_selectedZoneId.value, ZoneCommand(target, action))
                .onSuccess {
                    fetchData()
                }
            _isManualLoading.value = false
        }
    }

    fun scheduleDeviceOff(target: String, minutes: Long) {
        val inputData = Data.Builder()
            .putString("zone_id", _selectedZoneId.value)
            .putString("target", target)
            .putString("action", "off")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DeviceControlWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
        sendCommand(target, "on")
    }

    // --- Local Scheduling Logic ---

    fun addSchedule(zoneId: String, deviceName: String, target: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val schedule = ScheduleEntity(
                zoneId = zoneId,
                deviceName = deviceName,
                hour = hour,
                minute = minute
            )
            val id = scheduleDao.insertSchedule(schedule).toInt()
            reScheduleWork(id, schedule.copy(id = id), target)
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            schedule.workId?.let { workManager.cancelAllWorkByTag(it) }
            scheduleDao.deleteSchedule(schedule)
        }
    }

    fun updateSchedule(schedule: ScheduleEntity, target: String) {
        viewModelScope.launch {
            reScheduleWork(schedule.id, schedule, target)
        }
    }

    fun toggleScheduleStatus(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val newStatus = !schedule.isActive
            if (newStatus) {
                // Determine target based on device name
                val target = when (schedule.deviceName) {
                    "Water Pump" -> "water_pump"
                    "Fan" -> "fan"
                    "Grow Light" -> "grow_light"
                    else -> "unknown"
                }
                reScheduleWork(schedule.id, schedule.copy(isActive = true), target)
            } else {
                schedule.workId?.let { workManager.cancelAllWorkByTag(it) }
                scheduleDao.updateSchedule(schedule.copy(isActive = false, workId = null))
            }
        }
    }

    private suspend fun reScheduleWork(id: Int, schedule: ScheduleEntity, target: String) {
        // Cancel existing work if any
        schedule.workId?.let { workManager.cancelAllWorkByTag(it) }

        val delay = calculateDelay(schedule.hour, schedule.minute)
        val workTag = "schedule_work_$id"
        
        val inputData = Data.Builder()
            .putString("zone_id", schedule.zoneId)
            .putString("device_name", schedule.deviceName)
            .putString("target", target)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(workTag, ExistingWorkPolicy.REPLACE, workRequest)
        scheduleDao.updateSchedule(schedule.copy(workId = workTag))
    }

    private fun calculateDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (scheduledTime.before(now)) {
            scheduledTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        return scheduledTime.timeInMillis - now.timeInMillis
    }
}
