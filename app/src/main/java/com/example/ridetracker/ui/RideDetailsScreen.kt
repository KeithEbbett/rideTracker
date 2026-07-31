package com.example.ridetracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ridetracker.data.model.Ride
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RideDetailsScreen(
    rideId: Long,
    viewModel: RideViewModel,
    onBack: () -> Unit
) {
    var ride by remember { mutableStateOf<Ride?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val isMetric by viewModel.isMetric.collectAsState()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    LaunchedEffect(rideId) {
        ride = viewModel.getRideById(rideId)
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Ride") },
            text = { Text("Are you sure you want to delete this ride? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ride?.let { 
                            viewModel.deleteRide(it)
                            onBack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Ride Details", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        ride?.let { r ->
            Text(
                text = dateFormat.format(Date(r.startTime)),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val speedFactor = if (isMetric) 3.6 else 2.23694
            val speedUnit = if (isMetric) "km/h" else "mph"
            val distanceFactor = if (isMetric) 1000.0 else 1609.34
            val distanceUnit = if (isMetric) "km" else "miles"
            val elevationFactor = if (isMetric) 1.0 else 3.28084
            val elevationUnit = if (isMetric) "m" else "ft"

            MetricRow(label = "Distance", value = "%.2f %s".format(r.distance / distanceFactor, distanceUnit))
            MetricRow(label = "Avg Speed", value = "%.1f %s".format(r.avgSpeed * speedFactor, speedUnit))
            MetricRow(label = "Max Speed", value = "%.1f %s".format(r.maxSpeed * speedFactor, speedUnit))
            MetricRow(label = "Elevation Gain", value = "%.0f %s".format(r.totalElevationGain * elevationFactor, elevationUnit))
            if (r.averageHeartRate != null) {
                MetricRow(label = "Avg Heart Rate", value = "${r.averageHeartRate} bpm")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.uploadToStrava(r) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Upload to Strava")
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Ride")
            }

            Spacer(modifier = Modifier.height(8.dp))
        } ?: run {
            CircularProgressIndicator()
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
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
