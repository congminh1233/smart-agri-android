package com.example.agriiot.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val zoneId: String,
    val deviceName: String,
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    val workId: String? = null // To store the WorkManager UUID for cancellation
)
