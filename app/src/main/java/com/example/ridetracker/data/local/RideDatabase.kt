package com.example.ridetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint

@Database(entities = [Ride::class, RidePoint::class], version = 1, exportSchema = false)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
