package com.example.agriiot.data.model

import com.google.gson.annotations.SerializedName

data class ZoneState(
    @SerializedName("zone_id") val zoneId: String,
    val telemetry: Telemetry?,
    @SerializedName("actuator_status") val actuator: Actuator?
)

data class Telemetry(
    @SerializedName("air_temperature") val airTemperature: Float,
    @SerializedName("soil_moisture") val soilMoisture: Float,
    @SerializedName("water_tank_level") val waterLevel: Float,
    @SerializedName("light_lux") val light: Float
)

data class Actuator(
    @SerializedName("water_pump") val waterPump: String?,
    val fan: String?,
    @SerializedName("grow_light") val growLight: String?
)

data class ZoneCommand(
    val target: String,
    val action: String,
    val reason: String = "manual_app_control"
)

data class CommandResponse(
    val status: String,
    val message: String,
    val command: ZoneCommand? = null
)

data class EventItem(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("zone_id") val zoneId: String,
    @SerializedName("device_id") val deviceId: String,
    val message: String,
    val severity: String,
    val timestamp: String,
    @SerializedName("water_tank_level") val waterTankLevel: Float? = null
)

data class EventResponse(
    @SerializedName("zone_id") val zoneId: String,
    val events: List<EventItem>,
    val returned: Int,
    val total: Int
)
