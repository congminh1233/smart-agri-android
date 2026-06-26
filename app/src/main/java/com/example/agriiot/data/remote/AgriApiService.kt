package com.example.agriiot.data.remote

import com.example.agriiot.data.model.EventResponse
import com.example.agriiot.data.model.ZoneCommand
import com.example.agriiot.data.model.ZoneState
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AgriApiService {
    @GET("zones")
    suspend fun getZones(): List<String>

    @GET("zones/{zone_id}/state")
    suspend fun getZoneState(@Path("zone_id") zoneId: String): ZoneState

    @POST("zones/{zone_id}/command")
    suspend fun sendCommand(
        @Path("zone_id") zoneId: String,
        @Body command: ZoneCommand
    ): Map<String, String>

    @GET("zones/{zone_id}/events")
    suspend fun getZoneEvents(
        @Path("zone_id") zoneId: String,
        @Query("limit") limit: Int = 20
    ): EventResponse
}
