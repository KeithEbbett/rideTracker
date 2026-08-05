package com.example.ridetracker.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ridetracker.data.RideSessionManager
import com.example.ridetracker.data.sensor.BleManager
import com.example.ridetracker.ui.RideViewModel.SensorRole

@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(
    viewModel: RideViewModel,
    onBack: () -> Unit
) {
    val scannedDevices = viewModel.scannedDevices
    val isScanning by viewModel.isScanning.collectAsState()
    val rideState by viewModel.rideState.collectAsState()
    var sensorToPair by remember { mutableStateOf<BleManager.ScannedSensor?>(null) }

    if (sensorToPair != null) {
        RoleSelectionDialog(
            sensor = sensorToPair!!,
            onDismiss = { sensorToPair = null },
            onRoleSelected = { role ->
                viewModel.connectToDevice(sensorToPair!!.device, role)
                sensorToPair = null
            }
        )
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Back")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.startScanning() }, enabled = !isScanning) {
                        Text(if (isScanning) "Scanning..." else "Scan for Sensors")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                        Text(text = "Paired Sensors", style = MaterialTheme.typography.titleLarge)
                val pairedHr by viewModel.pairedHrMac.collectAsState()
                val pairedSpeed by viewModel.pairedSpeedMac.collectAsState()
                val pairedCadence by viewModel.pairedCadenceMac.collectAsState()

                if (pairedHr != null) {
                    PairedSensorItem(
                        label = "❤️ Heart Rate", 
                        mac = pairedHr!!, 
                        status = rideState.hrStatus,
                        onClear = { viewModel.clearSensor(SensorRole.HEART_RATE) }
                    )
                }
                if (pairedSpeed != null) {
                    PairedSensorItem(
                        label = "💨 Speed Sensor", 
                        mac = pairedSpeed!!, 
                        status = rideState.cscStatus,
                        onClear = { viewModel.clearSensor(SensorRole.SPEED) }
                    )
                }
                if (pairedCadence != null) {
                    if (pairedCadence != pairedSpeed) {
                        PairedSensorItem(
                            label = "🚲 Cadence Sensor", 
                            mac = pairedCadence!!, 
                            status = rideState.cscStatus,
                            onClear = { viewModel.clearSensor(SensorRole.CADENCE) }
                        )
                    } else {
                        Text(text = "Combined Speed & Cadence Active", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item {
                if (rideState.lastRawPackets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Live Data Debug", style = MaterialTheme.typography.titleMedium)
                    rideState.lastRawPackets.forEach { packet ->
                        Text(text = packet, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Units & Appearance", style = MaterialTheme.typography.titleLarge)
                
                val isMetric by viewModel.isMetric.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Use Metric Units (km/h, km)")
                    Switch(
                        checked = isMetric,
                        onCheckedChange = { viewModel.setMetric(it) }
                    )
                }

                val isHighContrast by viewModel.isHighContrast.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Daylight High Contrast Mode")
                    Switch(
                        checked = isHighContrast,
                        onCheckedChange = { viewModel.setHighContrast(it) }
                    )
                }

                val isBatterySaver by viewModel.isBatterySaver.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "AMOLED Battery Saver (Pure Black)")
                    Switch(
                        checked = isBatterySaver,
                        onCheckedChange = { viewModel.setBatterySaver(it) }
                    )
                }

                val isKeepScreenOn by viewModel.isKeepScreenOn.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Keep Screen On")
                    Switch(
                        checked = isKeepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) }
                    )
                }

                val isAutoPauseEnabled by viewModel.isAutoPauseEnabled.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Auto-Pause (below 1.8 km/h)")
                    Switch(
                        checked = isAutoPauseEnabled,
                        onCheckedChange = { viewModel.setAutoPauseEnabled(it) }
                    )
                }
            }

            item {
                val wheelCircumference by viewModel.wheelCircumference.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Wheel Circumference (mm)")
                    OutlinedTextField(
                        value = wheelCircumference.toString(),
                        onValueChange = { 
                            it.toIntOrNull()?.let { mm -> viewModel.setWheelCircumference(mm) }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (rideState.isTracking && rideState.isSpeedActive) {
                    val progress = (rideState.calibrationDistanceMeters / 300.0).coerceIn(0.0, 1.0)
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Auto-Calibration Progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LinearProgressIndicator(
                            progress = { progress.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(vertical = 4.dp),
                            color = if (progress >= 1.0) Color.Green else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (progress < 1.0) 
                                "Learning your wheel size... (${rideState.calibrationDistanceMeters.toInt()}m / 300m)" 
                                else "Learning complete. Suggested fix available below.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                rideState.suggestedWheelCircumference?.let { suggested ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "✨ Suggested Calibration", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Based on GPS data, your wheel size appears to be $suggested mm (currently $wheelCircumference mm).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.setWheelCircumference(suggested) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("APPLY FIX NOW")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Strava Integration", style = MaterialTheme.typography.titleLarge)
                val isStravaConnected by viewModel.isStravaConnected.collectAsState()
                val uriHandler = LocalUriHandler.current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isStravaConnected) "Connected to Strava" else "Not connected")
                        Text(
                            text = if (isStravaConnected) "You can now upload rides from history." else "Connect to upload your rides automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Button(
                        onClick = { 
                            if (isStravaConnected) {
                                viewModel.disconnectStrava()
                            } else {
                                uriHandler.openUri(viewModel.getStravaLoginUrl())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStravaConnected) MaterialTheme.colorScheme.errorContainer else Color(0xFFFC4C02)
                        )
                    ) {
                        Text(
                            text = if (isStravaConnected) "Disconnect" else "Connect",
                            color = if (isStravaConnected) MaterialTheme.colorScheme.onErrorContainer else Color.White
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "🛠 Troubleshooting", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Sensors not sensing? Ensure other cycling apps (Strava, Wahoo, etc.) are completely closed. Bluetooth sensors can usually only talk to one app at a time.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Discovered Devices", style = MaterialTheme.typography.titleLarge)
            }

            items(scannedDevices) { sensor ->
                val typeLabel = when (sensor.type) {
                    BleManager.SensorType.HEART_RATE -> "❤️ Heart Rate"
                    BleManager.SensorType.CYCLING_SPEED_CADENCE -> "🚲 Speed/Cadence"
                    else -> "❔ Unknown"
                }
                ListItem(
                    headlineContent = { Text(sensor.device.name ?: "Unknown Device") },
                    supportingContent = { Text("${sensor.device.address} ($typeLabel)") },
                    modifier = Modifier.clickable {
                        if (sensor.type == BleManager.SensorType.CYCLING_SPEED_CADENCE) {
                            sensorToPair = sensor
                        } else {
                            viewModel.connectToDevice(sensor.device, SensorRole.HEART_RATE)
                        }
                    }
                )
                HorizontalDivider()
            }
            
            item {
                Spacer(modifier = Modifier.height(100.dp)) // Extra space to scroll past the bottom bar
            }
        }
    }
}

@Composable
fun PairedSensorItem(
    label: String, 
    mac: String, 
    status: RideSessionManager.SensorStatus,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                val (statusText, statusColor) = when (status) {
                    RideSessionManager.SensorStatus.DISCONNECTED -> "Offline" to Color.Gray
                    RideSessionManager.SensorStatus.CONNECTING -> "Connecting..." to Color.Yellow
                    RideSessionManager.SensorStatus.CONNECTED -> "Connected" to Color.Green
                    RideSessionManager.SensorStatus.ERROR -> "Error" to Color.Red
                }
                Text(
                    text = "($statusText)", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = statusColor
                )
            }
            Text(text = mac, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = onClear) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Unpair", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun RoleSelectionDialog(
    sensor: BleManager.ScannedSensor,
    onDismiss: () -> Unit,
    onRoleSelected: (SensorRole) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Sensor Role") },
        text = { Text("How is this sensor mounted on your bike?") },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onRoleSelected(SensorRole.SPEED) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Speed Only (Wheel Hub)") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onRoleSelected(SensorRole.CADENCE) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cadence Only (Crank Arm)") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onRoleSelected(SensorRole.BOTH_SPEED_CADENCE) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Both (Combined Sensor)") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    )
}
