package com.example.ridetracker.ui

import com.example.ridetracker.BuildConfig
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.example.ridetracker.data.RideSessionManager
import com.example.ridetracker.data.model.Ride
import com.example.ridetracker.data.model.RidePoint
import com.example.ridetracker.data.repository.RideRepository
import com.example.ridetracker.data.sensor.BleManager
import com.example.ridetracker.data.strava.StravaRepository
import com.example.ridetracker.service.RideTrackingService
import com.example.ridetracker.util.GPXExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RideViewModel @Inject constructor(
    private val rideSessionManager: RideSessionManager,
    private val rideRepository: RideRepository,
    private val bleManager: BleManager,
    private val stravaRepository: StravaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("ride_tracker_prefs", Context.MODE_PRIVATE)

    val rideState = rideSessionManager.rideState

    val rideHistory: StateFlow<List<Ride>> = rideRepository.getAllRides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannedDevices = mutableStateListOf<BleManager.ScannedSensor>()
    val scannedDevices: List<BleManager.ScannedSensor> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isMetric = MutableStateFlow(sharedPrefs.getBoolean("is_metric", true))
    val isMetric = _isMetric.asStateFlow()

    private val _isHighContrast = MutableStateFlow(sharedPrefs.getBoolean("is_high_contrast", false))
    val isHighContrast = _isHighContrast.asStateFlow()

    private val _isAutoPauseEnabled = MutableStateFlow(sharedPrefs.getBoolean("auto_pause", true))
    val isAutoPauseEnabled = _isAutoPauseEnabled.asStateFlow()

    private val _isBatterySaver = MutableStateFlow(sharedPrefs.getBoolean("battery_saver", false))
    val isBatterySaver = _isBatterySaver.asStateFlow()

    private val _isKeepScreenOn = MutableStateFlow(sharedPrefs.getBoolean("keep_screen_on", false))
    val isKeepScreenOn = _isKeepScreenOn.asStateFlow()

    private val _pairedHrMac = MutableStateFlow(sharedPrefs.getString("hr_sensor_mac", null))
    val pairedHrMac = _pairedHrMac.asStateFlow()

    private val _pairedSpeedMac = MutableStateFlow(sharedPrefs.getString("speed_sensor_mac", null))
    val pairedSpeedMac = _pairedSpeedMac.asStateFlow()

    private val _pairedCadenceMac = MutableStateFlow(sharedPrefs.getString("cadence_sensor_mac", null))
    val pairedCadenceMac = _pairedCadenceMac.asStateFlow()

    private val _wheelCircumference = MutableStateFlow(sharedPrefs.getInt("wheel_circumference", 2096))
    val wheelCircumference = _wheelCircumference.asStateFlow()

    private val _isStravaConnected = MutableStateFlow(stravaRepository.isLoggedIn())
    val isStravaConnected = _isStravaConnected.asStateFlow()

    private val _stravaMessage = MutableStateFlow<String?>(null)
    val stravaMessage = _stravaMessage.asStateFlow()

    init {
        // Sync initial state to session manager
        rideSessionManager.updateState { it.copy(isAutoPauseEnabled = _isAutoPauseEnabled.value) }
    }

    fun setMetric(metric: Boolean) {
        _isMetric.value = metric
        sharedPrefs.edit().putBoolean("is_metric", metric).apply()
    }

    fun setHighContrast(highContrast: Boolean) {
        _isHighContrast.value = highContrast
        sharedPrefs.edit().putBoolean("is_high_contrast", highContrast).apply()
        if (highContrast) {
            _isBatterySaver.value = false
            sharedPrefs.edit().putBoolean("battery_saver", false).apply()
        }
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _isAutoPauseEnabled.value = enabled
        sharedPrefs.edit().putBoolean("auto_pause", enabled).apply()
        rideSessionManager.updateState { it.copy(isAutoPauseEnabled = enabled) }
    }

    fun setWheelCircumference(mm: Int) {
        _wheelCircumference.value = mm
        sharedPrefs.edit().putInt("wheel_circumference", mm).apply()
        // Clear suggestion and discrepancy instantly in state
        rideSessionManager.updateState { it.copy(suggestedWheelCircumference = null, speedDiscrepancy = null) }
    }

    fun setBatterySaver(enabled: Boolean) {
        _isBatterySaver.value = enabled
        sharedPrefs.edit().putBoolean("battery_saver", enabled).apply()
        if (enabled) {
            _isHighContrast.value = false
            sharedPrefs.edit().putBoolean("is_high_contrast", false).apply()
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _isKeepScreenOn.value = enabled
        sharedPrefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun toggleTracking() {
        if (rideState.value.isTracking) {
            if (rideState.value.isManuallyPaused) {
                resumeRide()
            } else {
                pauseRide()
            }
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        val intent = Intent(context, RideTrackingService::class.java).apply {
            action = RideTrackingService.ACTION_START
        }
        context.startService(intent)
    }

    fun pauseRide() {
        val intent = Intent(context, RideTrackingService::class.java).apply {
            action = RideTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRide() {
        val intent = Intent(context, RideTrackingService::class.java).apply {
            action = RideTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun finishRide() {
        val intent = Intent(context, RideTrackingService::class.java).apply {
            action = RideTrackingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun startScanning() {
        _scannedDevices.clear()
        _isScanning.value = true
        bleManager.scanAndConnect { sensor ->
            if (!_scannedDevices.any { it.device.address == sensor.device.address }) {
                _scannedDevices.add(sensor)
            }
        }
    }

    enum class SensorRole { HEART_RATE, SPEED, CADENCE, BOTH_SPEED_CADENCE }

    fun connectToDevice(device: android.bluetooth.BluetoothDevice, role: SensorRole) {
        val editor = sharedPrefs.edit()
        when (role) {
            SensorRole.HEART_RATE -> {
                _pairedHrMac.value = device.address
                editor.putString("hr_sensor_mac", device.address)
            }
            SensorRole.SPEED -> {
                _pairedSpeedMac.value = device.address
                editor.putString("speed_sensor_mac", device.address)
            }
            SensorRole.CADENCE -> {
                _pairedCadenceMac.value = device.address
                editor.putString("cadence_sensor_mac", device.address)
            }
            SensorRole.BOTH_SPEED_CADENCE -> {
                _pairedSpeedMac.value = device.address
                _pairedCadenceMac.value = device.address
                editor.putString("speed_sensor_mac", device.address)
                editor.putString("cadence_sensor_mac", device.address)
            }
        }
        editor.apply()
    }

    fun clearSensor(role: SensorRole) {
        val editor = sharedPrefs.edit()
        when (role) {
            SensorRole.HEART_RATE -> {
                _pairedHrMac.value = null
                editor.remove("hr_sensor_mac")
            }
            SensorRole.SPEED -> {
                _pairedSpeedMac.value = null
                editor.remove("speed_sensor_mac")
            }
            SensorRole.CADENCE -> {
                _pairedCadenceMac.value = null
                editor.remove("cadence_sensor_mac")
            }
            SensorRole.BOTH_SPEED_CADENCE -> {
                _pairedSpeedMac.value = null
                _pairedCadenceMac.value = null
                editor.remove("speed_sensor_mac")
                editor.remove("cadence_sensor_mac")
            }
        }
        editor.apply()
    }

    fun getPointsForRide(rideId: Long) = rideRepository.getPointsForRide(rideId)

    suspend fun getRideById(rideId: Long) = rideRepository.getRideById(rideId)

    fun deleteRide(ride: Ride) {
        viewModelScope.launch {
            rideRepository.deleteRide(ride)
        }
    }

    fun uploadToStrava(ride: Ride) {
        viewModelScope.launch {
            _stravaMessage.value = "Uploading to Strava..."
            try {
                val points = rideRepository.getPointsForRide(ride.id).first()
                if (points.isEmpty()) {
                    _stravaMessage.value = "Upload failed: No GPS data found for this ride."
                    return@launch
                }
                val file = File(context.cacheDir, "ride_${ride.id}.gpx")
                GPXExporter.export(ride, points, file)
                stravaRepository.uploadActivity(file)
                _stravaMessage.value = "Successfully uploaded to Strava!"
            } catch (e: Exception) {
                Timber.e(e, "Strava upload failed")
                _stravaMessage.value = "Upload failed: ${e.localizedMessage}"
            }
        }
    }

    fun clearStravaMessage() {
        _stravaMessage.value = null
    }

    fun getStravaLoginUrl(): String {
        return "https://www.strava.com/oauth/authorize" +
                "?client_id=${BuildConfig.STRAVA_CLIENT_ID}" +
                "&response_type=code" +
                "&redirect_uri=ridetracker://localhost" +
                "&approval_prompt=force" +
                "&scope=read,activity:write"
    }

    fun handleStravaCode(code: String) {
        viewModelScope.launch {
            try {
                stravaRepository.completeLogin(code)
                _isStravaConnected.value = true
            } catch (e: Exception) {
                Timber.e(e, "Strava login failed")
            }
        }
    }

    fun disconnectStrava() {
        stravaRepository.logout()
        _isStravaConnected.value = false
    }
}
