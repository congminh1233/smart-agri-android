package com.example.agriiot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.agriiot.data.model.Event
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.model.ZoneState
import com.example.agriiot.data.repository.ZoneRepository
import com.example.agriiot.worker.DeviceControlWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: ZoneState) : UiState()
    data class Error(val message: String) : UiState()
}

@HiltViewModel
class ZoneViewModel @Inject constructor(
    private val repository: ZoneRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _criticalEvent = MutableSharedFlow<Event>()
    val criticalEvent: SharedFlow<Event> = _criticalEvent.asSharedFlow()

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null
    private val zoneId = "zone1" // Default zone

    init {
        startPolling()
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchData()
                checkEvents()
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
            checkEvents()
        }
    }

    private suspend fun fetchData() {
        repository.getZoneState(zoneId).onSuccess {
            _uiState.value = UiState.Success(it)
        }.onFailure {
            _uiState.value = UiState.Error(it.message ?: "Unknown error")
        }
    }

    private suspend fun checkEvents() {
        repository.getLatestEvents(zoneId).onSuccess { events ->
            events.firstOrNull { it.severity == "critical" }?.let {
                _criticalEvent.emit(it)
            }
        }
    }

    fun sendCommand(target: String, action: String) {
        if (_isManualLoading.value) return
        
        viewModelScope.launch {
            _isManualLoading.value = true
            repository.sendCommand(zoneId, ZoneCommand(target, action))
                .onSuccess {
                    fetchData() // Refresh state immediately after command
                }
            _isManualLoading.value = false
        }
    }

    fun scheduleDeviceOff(target: String, minutes: Long) {
        val inputData = Data.Builder()
            .putString("zone_id", zoneId)
            .putString("target", target)
            .putString("action", "off")
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DeviceControlWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .build()

        workManager.enqueue(workRequest)
        
        // Also turn it ON immediately as per requirements
        sendCommand(target, "on")
    }
}
