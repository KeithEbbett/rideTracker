package com.example.ridetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distance: Double = 0.0,
    val avgSpeed: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val totalElevationGain: Double = 0.0,
    val averageHeartRate: Int? = null
)
