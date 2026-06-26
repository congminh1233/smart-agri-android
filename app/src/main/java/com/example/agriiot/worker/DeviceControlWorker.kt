package com.example.agriiot.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.repository.ZoneRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DeviceControlWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ZoneRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val zoneId = inputData.getString("zone_id") ?: return Result.failure()
        val target = inputData.getString("target") ?: return Result.failure()
        val action = inputData.getString("action") ?: "off"

        val command = ZoneCommand(target, action, "scheduled_worker")
        val response = repository.sendCommand(zoneId, command)

        return if (response.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
