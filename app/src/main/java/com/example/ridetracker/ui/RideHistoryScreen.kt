package com.example.ridetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RideHistoryScreen(
    viewModel: RideViewModel,
    onRideClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val rides by viewModel.rideHistory.collectAsState()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Ride History", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rides) { ride ->
                ListItem(
                    headlineContent = { 
                        Text("Ride on ${dateFormat.format(Date(ride.startTime))}") 
                    },
                    supportingContent = { 
                        Text("%.2f km - %.1f km/h avg".format(ride.distance / 1000, ride.avgSpeed * 3.6)) 
                    },
                    modifier = Modifier.clickable { onRideClick(ride.id) }
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
