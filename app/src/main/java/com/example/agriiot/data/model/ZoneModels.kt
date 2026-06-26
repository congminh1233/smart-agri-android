package com.example.agriiot.data.model

import com.google.gson.annotations.SerializedName

data class ZoneState(
    @SerializedName("zone_id") val zoneId: String,
    val telemetry: Telemetry,
    val actuator: Actuator
)

data class Telemetry(
    @SerializedName("air_temperature") val airTemperature: Float,
    @SerializedName("soil_moisture") val soilMoisture: Float,
    @SerializedName("water_level") val waterLevel: Float,
    val light: Float
)

data class Actuator(
    @SerializedName("water_pump") val waterPump: String,
    val fan: String,
    @SerializedName("grow_light") val growLight: String
)

data class ZoneCommand(
    val target: String,
    val action: String,
    val reason: String = "manual_app_control"
)

data class Event(
    val id: String,
    @SerializedName("zone_id") val zoneId: String,
    @SerializedName("event_type") val eventType: String,
    val severity: String,
    val description: String,
    val timestamp: String
)
