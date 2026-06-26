package com.example.agriiot.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.agriiot.data.local.SharedPrefsHelper
import com.example.agriiot.data.repository.ZoneRepository
import com.example.agriiot.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EventPollingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ZoneRepository,
    private val sharedPrefsHelper: SharedPrefsHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lastSeenTimestamp = sharedPrefsHelper.getLastSeenTimestamp()
        var maxTimestamp = lastSeenTimestamp

        val zonesResult = repository.getAllZones()
        if (zonesResult.isFailure) return Result.retry()

        val zones = zonesResult.getOrNull() ?: emptyList()
        var newCriticalCount = 0
        val summaryMessages = mutableListOf<String>()

        for (zoneId in zones) {
            val eventsResult = repository.getZoneEvents(zoneId, limit = 5)
            if (eventsResult.isSuccess) {
                val events = eventsResult.getOrNull()?.events ?: emptyList()
                val newCriticalEvents = events.filter { 
                    it.timestamp > lastSeenTimestamp && it.severity == "critical" 
                }

                if (newCriticalEvents.isNotEmpty()) {
                    newCriticalCount += newCriticalEvents.size
                    newCriticalEvents.forEach { event ->
                        summaryMessages.add("[$zoneId] ${event.message}")
                    }
                }

                // Track the absolute maximum timestamp found
                events.maxByOrNull { it.timestamp }?.let {
                    if (it.timestamp > maxTimestamp) {
                        maxTimestamp = it.timestamp
                    }
                }
            }
        }

        if (newCriticalCount > 0) {
            val title = "Agriculture Alert: $newCriticalCount New Critical Events"
            val message = summaryMessages.take(3).joinToString("\n") + 
                (if (summaryMessages.size > 3) "\n..." else "")
            
            NotificationHelper.showNotification(applicationContext, title, message)
        }

        sharedPrefsHelper.setLastSeenTimestamp(maxTimestamp)
        return Result.success()
    }
}
