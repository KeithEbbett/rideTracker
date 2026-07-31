package com.example.ridetracker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideSessionManager @Inject constructor() {
    enum class SpeedSource { GPS, SENSOR }

    data class RideState(
        val durationMillis: Long = 0,
        val distanceMeters: Double = 0.0,
        val speedMps: Double = 0.0,
        val speedSource: SpeedSource = SpeedSource.GPS,
        val heartRate: Int = 0,
        val cadence: Int = 0,
        val gradient: Double = 0.0,
        val isTracking: Boolean = false,
        val isAutoPaused: Boolean = false,
        val isManuallyPaused: Boolean = false,
        val isAutoPauseEnabled: Boolean = false,
        val isHrConnected: Boolean = false,
        val isCscConnected: Boolean = false, // Legacy, keeping for compatibility during migration
        val isSpeedActive: Boolean = false,
        val isCadenceActive: Boolean = false,
        val speedDiscrepancy: Double? = null,
        val suggestedWheelCircumference: Int? = null,
        val lastRawPackets: List<String> = emptyList()
    )

    private val _rideState = MutableStateFlow(RideState())
    val rideState: StateFlow<RideState> = _rideState.asStateFlow()

    fun updateState(update: (RideState) -> RideState) {
        _rideState.value = update(_rideState.value)
    }

    fun setTracking(isTracking: Boolean) {
        _rideState.value = _rideState.value.copy(isTracking = isTracking)
    }

    fun reset() {
        _rideState.value = RideState()
    }
}
