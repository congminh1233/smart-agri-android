package com.example.agriiot.data.repository

import com.example.agriiot.data.model.EventResponse
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.model.ZoneState
import com.example.agriiot.data.remote.AgriApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val apiService: AgriApiService
) {
    suspend fun getAllZones(): Result<List<String>> = runCatching {
        apiService.getZones()
    }

    suspend fun getZoneState(zoneId: String): Result<ZoneState> = runCatching {
        apiService.getZoneState(zoneId)
    }

    suspend fun sendCommand(zoneId: String, command: ZoneCommand): Result<Map<String, String>> = runCatching {
        apiService.sendCommand(zoneId, command)
    }

    suspend fun getZoneEvents(zoneId: String, limit: Int = 20): Result<EventResponse> = runCatching {
        apiService.getZoneEvents(zoneId, limit)
    }
}
