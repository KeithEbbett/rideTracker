package com.example.ridetracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridetracker.data.RideSessionManager

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: RideViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val rideState by viewModel.rideState.collectAsState()
    val isMetric by viewModel.isMetric.collectAsState()
    var showMismatchDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val speedValue = if (isMetric) rideState.speedMps * 3.6 else rideState.speedMps * 2.23694
    val speedUnit = if (isMetric) "km/h" else "mph"
    val distanceValue = if (isMetric) rideState.distanceMeters / 1000 else rideState.distanceMeters / 1609.34
    val distanceUnit = if (isMetric) "km" else "miles"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        if (rideState.isAutoPaused) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "AUTO-PAUSED",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = when (rideState.cscStatus) {
                        RideSessionManager.SensorStatus.CONNECTED -> if (rideState.isSpeedActive) Color.Green else Color.Gray
                        RideSessionManager.SensorStatus.CONNECTING -> Color.Yellow.copy(alpha = alpha)
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Speed", style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%.1f".format(speedValue),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold
                )
                if ((rideState.speedDiscrepancy ?: 0.0) > 0.1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showMismatchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Speed Mismatch",
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (showMismatchDialog) {
                AlertDialog(
                    onDismissRequest = { showMismatchDialog = false },
                    title = { Text("Speed Mismatch Detected") },
                    text = { 
                        val percent = ((rideState.speedDiscrepancy ?: 0.0) * 100).toInt()
                        Text("Your wheel sensor and GPS speed differ by $percent%. This usually means your Wheel Circumference setting is incorrect.") 
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            showMismatchDialog = false
                            onNavigateToSettings() 
                        }) {
                            Text("Go to Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMismatchDialog = false }) {
                            Text("Dismiss")
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = speedUnit, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (rideState.speedSource == RideSessionManager.SpeedSource.SENSOR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = rideState.speedSource.name,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem(
                label = "Heart Rate", 
                value = "${rideState.heartRate} bpm",
                icon = Icons.Default.Favorite,
                iconColor = when (rideState.hrStatus) {
                    RideSessionManager.SensorStatus.CONNECTED -> Color.Green
                    RideSessionManager.SensorStatus.CONNECTING -> Color.Yellow.copy(alpha = alpha)
                    else -> Color.Gray
                }
            )
            MetricItem(
                label = "Cadence", 
                value = "${rideState.cadence} rpm",
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                iconColor = when (rideState.cscStatus) {
                    RideSessionManager.SensorStatus.CONNECTED -> if (rideState.isCadenceActive) Color.Green else Color.Gray
                    RideSessionManager.SensorStatus.CONNECTING -> Color.Yellow.copy(alpha = alpha)
                    else -> Color.Gray
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem(label = "Distance", value = "%.2f %s".format(distanceValue, distanceUnit))
            MetricItem(label = "Time", value = formatDuration(rideState.durationMillis))
            MetricItem(label = "Gradient", value = "%.1f %%".format(rideState.gradient))
        }

        val buttonText = when {
            !rideState.isTracking -> "START RIDE"
            rideState.isManuallyPaused -> "RESUME"
            else -> "PAUSE"
        }

        Surface(
            color = if (rideState.isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .combinedClickable(
                    onClick = { viewModel.toggleTracking() },
                    onLongClick = { 
                        if (rideState.isTracking && rideState.isManuallyPaused) {
                            viewModel.finishRide()
                        }
                    }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = buttonText,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (rideState.isTracking && rideState.isManuallyPaused) {
                        Text(
                            text = "Hold to Finish",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onNavigateToHistory) {
                Text("History")
            }
            OutlinedButton(onClick = onNavigateToSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String, 
    value: String, 
    icon: ImageVector? = null,
    iconColor: Color = Color.Gray
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
        Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
