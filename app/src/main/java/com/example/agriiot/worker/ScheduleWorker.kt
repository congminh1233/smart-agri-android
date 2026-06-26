package com.example.agriiot.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.repository.ZoneRepository
import com.example.agriiot.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ZoneRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val zoneId = inputData.getString("zone_id") ?: return Result.failure()
        val deviceName = inputData.getString("device_name") ?: return Result.failure()
        val target = inputData.getString("target") ?: return Result.failure()

        val command = ZoneCommand(target, "on", "automatic_schedule")
        val response = repository.sendCommand(zoneId, command)

        if (response.isSuccess) {
            NotificationHelper.showNotification(
                applicationContext,
                "Schedule Started",
                "Lịch $deviceName tại $zoneId đã bắt đầu chạy!"
            )
            return Result.success()
        }

        return Result.retry()
    }
}
