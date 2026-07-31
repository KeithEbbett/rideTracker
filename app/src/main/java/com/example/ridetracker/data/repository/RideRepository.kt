package com.example.ridetracker.data.repository

import com.example.ridetracker.data.local.RideDao
import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface RideRepository {
    fun getAllRides(): Flow<List<Ride>>
    suspend fun getRideById(rideId: Long): Ride?
    suspend fun insertRide(ride: Ride): Long
    suspend fun updateRide(ride: Ride)
    suspend fun deleteRide(ride: Ride)
    suspend fun insertRidePoint(point: RidePoint)
    fun getPointsForRide(rideId: Long): Flow<List<RidePoint>>
}

@Singleton
class RideRepositoryImpl @Inject constructor(
    private val rideDao: RideDao
) : RideRepository {
    override fun getAllRides(): Flow<List<Ride>> = rideDao.getAllRides()
    override suspend fun getRideById(rideId: Long): Ride? = rideDao.getRideById(rideId)
    override suspend fun insertRide(ride: Ride): Long = rideDao.insertRide(ride)
    override suspend fun updateRide(ride: Ride) = rideDao.updateRide(ride)
    override suspend fun deleteRide(ride: Ride) = rideDao.deleteRide(ride)
    override suspend fun insertRidePoint(point: RidePoint) = rideDao.insertRidePoint(point)
    override fun getPointsForRide(rideId: Long): Flow<List<RidePoint>> = rideDao.getPointsForRide(rideId)
}
