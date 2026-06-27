package com.example.agriiot.worker

import android.content.Context
import android.util.Log
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
        val zoneId = inputData.getString("zone_id")
        val deviceName = inputData.getString("device_name")
        val target = inputData.getString("target")

        Log.d("ScheduleWorker", "Worker triggered! Zone: $zoneId, Device: $deviceName, Target: $target")

        if (zoneId == null || deviceName == null || target == null) {
            Log.e("ScheduleWorker", "Missing input data! Aborting.")
            return Result.failure()
        }

        val command = ZoneCommand(target, "on", "automatic_schedule")
        Log.d("ScheduleWorker", "Sending command: $command to zone: $zoneId")
        
        val response = repository.sendCommand(zoneId, command)

        return if (response.isSuccess) {
            Log.d("ScheduleWorker", "Command sent successfully.")
            NotificationHelper.showNotification(
                applicationContext,
                "Schedule Started",
                "Lịch $deviceName tại $zoneId đã bắt đầu chạy!"
            )
            Result.success()
        } else {
            Log.e("ScheduleWorker", "Failed to send command: ${response.exceptionOrNull()?.message}")
            Result.retry()
        }
    }
}
