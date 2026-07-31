package com.example.ridetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: Ride): Long

    @Update
    suspend fun updateRide(ride: Ride)

    @Delete
    suspend fun deleteRide(ride: Ride)

    @Query("SELECT * FROM rides ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: Long): Ride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRidePoint(point: RidePoint)

    @Query("SELECT * FROM ride_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    fun getPointsForRide(rideId: Long): Flow<List<RidePoint>>
}
