package com.example.ridetracker.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Paired Sensors", style = MaterialTheme.typography.titleLarge)
        val pairedHr by viewModel.pairedHrMac.collectAsState()
        val pairedSpeed by viewModel.pairedSpeedMac.collectAsState()
        val pairedCadence by viewModel.pairedCadenceMac.collectAsState()

        if (pairedHr != null) {
            PairedSensorItem(label = "❤️ Heart Rate", mac = pairedHr!!, onClear = { viewModel.clearSensor(SensorRole.HEART_RATE) })
        }
        if (pairedSpeed != null) {
            PairedSensorItem(label = "💨 Speed Sensor", mac = pairedSpeed!!, onClear = { viewModel.clearSensor(SensorRole.SPEED) })
        }
        if (pairedCadence != null) {
            if (pairedCadence != pairedSpeed) {
                PairedSensorItem(label = "🚲 Cadence Sensor", mac = pairedCadence!!, onClear = { viewModel.clearSensor(SensorRole.CADENCE) })
            } else {
                Text(text = "Speed Sensor also providing Cadence", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (rideState.lastRawPackets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Live Data Debug", style = MaterialTheme.typography.titleMedium)
            rideState.lastRawPackets.forEach { packet ->
                Text(text = packet, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }

        rideState.suggestedWheelCircumference?.let { suggested ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.setWheelCircumference(suggested) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Suggested Calibration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Based on your GPS data, we suggest setting your wheel circumference to $suggested mm.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Tap here to apply",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Discovered Devices", style = MaterialTheme.typography.titleLarge)
        
        LazyColumn(modifier = Modifier.weight(1f)) {
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
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
fun PairedSensorItem(label: String, mac: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
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
