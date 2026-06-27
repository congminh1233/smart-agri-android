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
        val lastSeenTimestamp = sharedPrefsHelper.getLastSeenTimestamp()
        val allZones = _availableZones.value
        val selectedZone = _selectedZoneId.value
        
        val criticalEventsToNotify = mutableListOf<EventItem>()
        var globalMaxTimestamp = lastSeenTimestamp

        // 1. Cập nhật UI cho Zone hiện tại và quét cảnh báo cho TẤT CẢ các Zone
        allZones.forEach { zoneId ->
            // Sử dụng limit cao hơn cho zone đang chọn để hiển thị UI đầy đủ, 
            // limit thấp cho các zone khác để tối ưu performance
            val fetchLimit = if (zoneId == selectedZone) 20 else 5
            
            repository.getZoneEvents(zoneId, limit = fetchLimit).onSuccess { response ->
                val events = response.events
                
                // Cập nhật giao diện nếu là zone đang được người dùng xem
                if (zoneId == selectedZone) {
                    _events.value = events
                }

                // 2. Lọc các sự kiện nguy cấp mới (critical hoặc water_tank_low)
                val newCritical = events.filter { 
                    it.timestamp > lastSeenTimestamp && 
                    (it.severity == "critical" || it.eventType == "water_tank_low") 
                }
                criticalEventsToNotify.addAll(newCritical)

                // 4. Tìm timestamp lớn nhất toàn cục để cập nhật sau này
                events.maxByOrNull { it.timestamp }?.let {
                    if (it.timestamp > globalMaxTimestamp) {
                        globalMaxTimestamp = it.timestamp
                    }
                }
            }
        }

        // 3. Gộp thông báo (Chống spam "Muting recently noisy")
        if (criticalEventsToNotify.isNotEmpty()) {
            val count = criticalEventsToNotify.size
            val latestEvent = criticalEventsToNotify.maxBy { it.timestamp }
            
            // Emit sự kiện mới nhất lên UI flow
            viewModelScope.launch {
                _criticalEvent.emit(latestEvent)
            }

            val title: String
            val message: String

            if (count == 1) {
                title = "Alert [${latestEvent.zoneId}]: ${latestEvent.eventType.replace("_", " ").uppercase()}"
                message = latestEvent.message
            } else {
                title = "Agriculture Alert: $count New Critical Events"
                message = "Latest: [${latestEvent.zoneId}] ${latestEvent.message}"
            }

            // Chỉ bắn 1 thông báo duy nhất sau khi đã duyệt qua tất cả các zone
            NotificationHelper.showNotification(getApplication(), title, message)
        }

        // Cập nhật Timestamp mới nhất vào SharedPrefs
        if (globalMaxTimestamp > lastSeenTimestamp) {
            sharedPrefsHelper.setLastSeenTimestamp(globalMaxTimestamp)
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("zone_id", _selectedZoneId.value)
            .putString("target", target)
            .putString("action", "off")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DeviceControlWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
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
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString("zone_id", schedule.zoneId)
            .putString("device_name", schedule.deviceName)
            .putString("target", target)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(workTag)
            .setConstraints(constraints)
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
