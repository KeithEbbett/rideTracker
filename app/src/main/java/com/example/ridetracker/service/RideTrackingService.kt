package com.example.ridetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.ridetracker.MainActivity
import com.example.ridetracker.R
import com.example.ridetracker.data.RideSessionManager
import com.example.ridetracker.data.location.LocationManager
import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint
import com.example.ridetracker.data.repository.RideRepository
import com.example.ridetracker.data.sensor.BleManager
import com.example.ridetracker.ui.RideViewModel.SensorRole
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RideTrackingService : LifecycleService() {

    @Inject lateinit var locationManager: LocationManager
    @Inject lateinit var bleManager: BleManager
    @Inject lateinit var rideRepository: RideRepository
    @Inject lateinit var rideSessionManager: RideSessionManager

    private var currentRideId: Long = -1
    private var lastLocation: android.location.Location? = null
    private var totalDistance = 0.0
    private var activeTimeMillis: Long = 0
    private var lastTickTime: Long = 0
    private val gradientWindow = mutableListOf<Pair<Double, Double>>() // distance, altitude

    private var lastWheelRevolutions: Long? = null
    private var lastWheelEventTime: Int? = null
    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null
    private var lastSensorUpdateMillis: Long = 0
    private var wheelCircumferenceMm: Int = 2096

    // Calibration and Discrepancy
    private var totalGpsDistanceForCalibration = 0.0
    private var totalWheelRevsForCalibration = 0L
    private val gpsSpeedBuffer = mutableListOf<Double>()
    private val sensorSpeedBuffer = mutableListOf<Double>()
    private val activeSensorJobs = mutableMapOf<String, Job>()

    // Statistics tracking
    private var maxSpeedMps = 0.0
    private var totalElevationGain = 0.0
    private var lastAltitude: Double? = null
    private var hrSum = 0L
    private var hrCount = 0

    // Battery Optimization
    private val pointBuffer = mutableListOf<RidePoint>()
    private var isScreenOn = true
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    updateLocationInterval()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    updateLocationInterval()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ride_tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val AUTO_PAUSE_THRESHOLD_MPS = 0.5 // ~1.8 km/h
        const val GRADIENT_WINDOW_METERS = 30.0
        const val SENSOR_TIMEOUT_MILLIS = 3000L
        const val BATCH_SIZE = 20
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
        activeSensorJobs.values.forEach { it.cancel() }
        activeSensorJobs.clear()
        flushBuffer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun pauseTracking() {
        rideSessionManager.updateState { it.copy(isManuallyPaused = true) }
        updateNotification("Paused")
    }

    private fun resumeTracking() {
        lastTickTime = System.currentTimeMillis()
        rideSessionManager.updateState { it.copy(isManuallyPaused = false) }
        updateNotification("Resuming ride...")
    }

    private fun updateLocationInterval() {
        val state = rideSessionManager.rideState.value
        val interval = when {
            state.isAutoPaused -> 30000L
            !isScreenOn -> 10000L
            else -> 2000L
        }
        locationManager.setInterval(interval)
        Timber.d("Updated location interval to $interval ms")
    }

    private fun startTracking() {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Ready to ride"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Ready to ride"))
        }

        rideSessionManager.reset()
        rideSessionManager.setTracking(true)
        val startTime = System.currentTimeMillis()
        lastTickTime = startTime
        activeTimeMillis = 0

        lifecycleScope.launch {
            launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    val delta = now - lastTickTime
                    lastTickTime = now

                    val state = rideSessionManager.rideState.value
                    if (state.isTracking && !state.isAutoPaused && !state.isManuallyPaused) {
                        activeTimeMillis += delta
                        rideSessionManager.updateState { it.copy(durationMillis = activeTimeMillis) }
                    }
                    
                    if (pointBuffer.size >= BATCH_SIZE) {
                        flushBuffer()
                    }
                    
                    delay(1000)
                }
            }

            val ride = Ride(startTime = startTime)
            currentRideId = rideRepository.insertRide(ride)
            
            launch {
                locationManager.getLocationUpdates().collectLatest { location ->
                    val currentState = rideSessionManager.rideState.value
                    if (currentState.isManuallyPaused) return@collectLatest

                    val speed = location.speed
                    
                    val shouldAutoPause = currentState.isAutoPauseEnabled && speed < AUTO_PAUSE_THRESHOLD_MPS
                    
                    if (shouldAutoPause) {
                        if (!currentState.isAutoPaused) {
                            rideSessionManager.updateState { it.copy(isAutoPaused = true, speedMps = 0.0) }
                            lastLocation = null // Reset last location to prevent distance jumps on resume
                            updateNotification("Auto-Paused")
                            updateLocationInterval()
                        }
                        return@collectLatest
                    } else if (currentState.isAutoPaused) {
                        rideSessionManager.updateState { it.copy(isAutoPaused = false) }
                        updateNotification("Resumed")
                        updateLocationInterval()
                    }

                    // Only update distance via GPS if we don't have a sensor active
                    if (currentState.speedSource == RideSessionManager.SpeedSource.GPS) {
                        lastLocation?.let {
                            totalDistance += it.distanceTo(location)
                        }
                    }

                    // Max Speed Tracking
                    val currentSpeed = if (currentState.speedSource == RideSessionManager.SpeedSource.SENSOR) currentState.speedMps else location.speed.toDouble()
                    if (currentSpeed > maxSpeedMps) {
                        maxSpeedMps = currentSpeed
                    }

                    // Elevation Gain Tracking
                    val currentAltitude = location.altitude
                    lastAltitude?.let {
                        if (currentAltitude > it) {
                            totalElevationGain += (currentAltitude - it)
                        }
                    }
                    lastAltitude = currentAltitude

                    // Track GPS data for calibration if accuracy is good
                    if (location.accuracy < 10.0) {
                        lastLocation?.let {
                            totalGpsDistanceForCalibration += it.distanceTo(location)
                        }
                        
                        gpsSpeedBuffer.add(location.speed.toDouble())
                        if (gpsSpeedBuffer.size > 5) gpsSpeedBuffer.removeAt(0)
                        
                        checkDiscrepancy()
                    }

                    lastLocation = location

                    val currentGradient = calculateGradient(totalDistance, location.altitude)
                    
                    rideSessionManager.updateState {
                        val finalSpeed = if (it.speedSource == RideSessionManager.SpeedSource.SENSOR) it.speedMps else location.speed.toDouble()
                        it.copy(
                            distanceMeters = totalDistance,
                            speedMps = finalSpeed,
                            gradient = currentGradient
                        )
                    }
                    
                    val point = RidePoint(
                        rideId = currentRideId,
                        timestamp = System.currentTimeMillis(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        speed = location.speed.toDouble()
                    )
                    pointBuffer.add(point)
                    
                    updateNotification("Distance: %.2f km".format(totalDistance / 1000))
                }
            }

        // Sensor Connection Logic
        val sharedPrefs = getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)
        wheelCircumferenceMm = sharedPrefs.getInt("wheel_circumference", 2096)
        
        val hrMac = sharedPrefs.getString("hr_sensor_mac", null)
        val speedMac = sharedPrefs.getString("speed_sensor_mac", null)
        val cadenceMac = sharedPrefs.getString("cadence_sensor_mac", null)

        val uniqueMacs = listOfNotNull(hrMac, speedMac, cadenceMac).distinct()
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter

        if (adapter != null) {
            uniqueMacs.forEachIndexed { index, mac ->
                val job = launch {
                    delay(index * 1000L) // Small delay between connection attempts
                    try {
                        val device = adapter.getRemoteDevice(mac)
                        bleManager.connectToDevice(device).collectLatest { data ->
                            lastSensorUpdateMillis = System.currentTimeMillis()
                            rideSessionManager.updateState { state ->
                                var newState = state
                                
                                data.rawValue?.let { raw ->
                                    val hex = raw.joinToString("") { "%02X".format(it) }
                                    val newPackets = (listOf(hex) + state.lastRawPackets).take(3)
                                    newState = newState.copy(lastRawPackets = newPackets)
                                }

                                // Map data based on which MAC sent it and what roles it has
                                if (mac == hrMac) {
                                    data.heartRate?.let { 
                                        newState = newState.copy(heartRate = it, isHrConnected = true)
                                        hrSum += it
                                        hrCount++
                                    }
                                }

                                if (mac == cadenceMac) {
                                    data.crankRevolutions?.let { revs ->
                                        data.lastCrankEventTime?.let { time ->
                                            if (lastCrankRevolutions != null && lastCrankEventTime != null) {
                                                val deltaRevs = if (revs >= lastCrankRevolutions!!) revs - lastCrankRevolutions!! else (65535 - lastCrankRevolutions!!) + revs
                                                val deltaTime = if (time >= lastCrankEventTime!!) time - lastCrankEventTime!! else (65535 - lastCrankEventTime!!) + time
                                                if (deltaTime > 0) {
                                                    val rpm = (deltaRevs.toDouble() * 1024.0 * 60.0) / deltaTime.toDouble()
                                                    newState = newState.copy(
                                                        cadence = rpm.toInt(), 
                                                        isCscConnected = true,
                                                        isCadenceActive = true
                                                    )
                                                }
                                            }
                                            lastCrankRevolutions = revs
                                            lastCrankEventTime = time
                                        }
                                    }
                                }

                                if (mac == speedMac) {
                                    data.wheelRevolutions?.let { revs ->
                                        data.lastWheelEventTime?.let { time ->
                                            if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                                                val deltaRevs = revs - lastWheelRevolutions!!
                                                val deltaTime = if (time >= lastWheelEventTime!!) time - lastWheelEventTime!! else (65535 - lastWheelEventTime!!) + time
                                                if (deltaTime > 0 && deltaRevs > 0) {
                                                    totalDistance += (deltaRevs.toDouble() * wheelCircumferenceMm.toDouble() / 1000.0)
                                                    totalWheelRevsForCalibration += deltaRevs
                                                    val speedMps = (deltaRevs.toDouble() * wheelCircumferenceMm.toDouble() / 1000.0) / (deltaTime.toDouble() / 1024.0)
                                                    sensorSpeedBuffer.add(speedMps)
                                                    if (sensorSpeedBuffer.size > 5) sensorSpeedBuffer.removeAt(0)
                                                    newState = newState.copy(
                                                        distanceMeters = totalDistance,
                                                        speedMps = speedMps,
                                                        speedSource = RideSessionManager.SpeedSource.SENSOR,
                                                        isCscConnected = true,
                                                        isSpeedActive = true
                                                    )
                                                    checkCalibration()
                                                }
                                            }
                                            lastWheelRevolutions = revs
                                            lastWheelEventTime = time
                                        }
                                    }
                                }
                                newState
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to connect to sensor at $mac")
                    }
                }
                activeSensorJobs[mac] = job
            }
        }

        // Watchdog and preference monitoring
        launch {
            while (true) {
                delay(1000)
                
                // Monitor circumference changes
                val currentMm = sharedPrefs.getInt("wheel_circumference", 2096)
                if (currentMm != wheelCircumferenceMm) {
                    wheelCircumferenceMm = currentMm
                }

                if (System.currentTimeMillis() - lastSensorUpdateMillis > SENSOR_TIMEOUT_MILLIS) {
                    if (rideSessionManager.rideState.value.speedSource == RideSessionManager.SpeedSource.SENSOR) {
                        rideSessionManager.updateState { 
                            it.copy(
                                speedSource = RideSessionManager.SpeedSource.GPS,
                                isSpeedActive = false,
                                isCadenceActive = false
                            ) 
                        }
                    }
                }
            }
        }
    }
}
    private fun stopTracking() {
        rideSessionManager.setTracking(false)
        lifecycleScope.launch {
            if (currentRideId != -1L) {
                val ride = rideRepository.getRideById(currentRideId)
                ride?.let {
                    val durationSeconds = (activeTimeMillis / 1000.0).coerceAtLeast(1.0)
                    val avgSpeed = totalDistance / durationSeconds
                    val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null
                    
                    rideRepository.updateRide(it.copy(
                        endTime = System.currentTimeMillis(), 
                        distance = totalDistance,
                        avgSpeed = avgSpeed,
                        maxSpeed = maxSpeedMps,
                        totalElevationGain = totalElevationGain,
                        averageHeartRate = avgHr
                    ))
                }
            }
            flushBuffer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }
    }

    private fun flushBuffer() {
        if (pointBuffer.isEmpty()) return
        val pointsToSave = ArrayList(pointBuffer)
        pointBuffer.clear()
        lifecycleScope.launch {
            pointsToSave.forEach { rideRepository.insertRidePoint(it) }
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ride Tracker")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun calculateGradient(currentDistance: Double, currentAltitude: Double): Double {
        gradientWindow.add(Pair(currentDistance, currentAltitude))

        // Remove points older than GRADIENT_WINDOW_METERS
        while (gradientWindow.size > 2 && currentDistance - gradientWindow.first().first > GRADIENT_WINDOW_METERS) {
            gradientWindow.removeAt(0)
        }

        if (gradientWindow.size < 2) return 0.0

        val first = gradientWindow.first()
        val last = gradientWindow.last()

        val distanceDelta = last.first - first.first
        if (distanceDelta < 5.0) return 0.0 // Don't calculate for very small distances to avoid noise

        val altitudeDelta = last.second - first.second
        return (altitudeDelta / distanceDelta) * 100.0
    }

    private fun checkDiscrepancy() {
        if (gpsSpeedBuffer.size < 5 || sensorSpeedBuffer.size < 5) return
        
        val avgGps = gpsSpeedBuffer.average()
        val avgSensor = sensorSpeedBuffer.average()
        
        if (avgGps > 1.0) { // Only check if moving
            val diff = kotlin.math.abs(avgGps - avgSensor) / avgGps
            rideSessionManager.updateState { it.copy(speedDiscrepancy = diff) }
        }
    }

    private fun checkCalibration() {
        // Only suggest calibration after 1km of good GPS data
        if (totalGpsDistanceForCalibration > 1000.0 && totalWheelRevsForCalibration > 0) {
            val suggested = (totalGpsDistanceForCalibration * 1000.0 / totalWheelRevsForCalibration).toInt()
            
            // If suggested is significantly different from current, update UI state
            if (kotlin.math.abs(suggested - wheelCircumferenceMm) > 20) {
                rideSessionManager.updateState { it.copy(suggestedWheelCircumference = suggested) }
            }
        }
    }
}
