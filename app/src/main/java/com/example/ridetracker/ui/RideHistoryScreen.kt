package com.example.ridetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val stravaMessage by viewModel.stravaMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    LaunchedEffect(stravaMessage) {
        stravaMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStravaMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Text(
                text = "Ride History", 
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        },
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
        ) {
            items(rides) { ride ->
                ListItem(
                    headlineContent = { 
                        Text("Ride on ${dateFormat.format(Date(ride.startTime))}") 
                    },
                    supportingContent = { 
                        Text("%.2f km - %.1f km/h avg".format(ride.distance / 1000, ride.avgSpeed * 3.6)) 
                    },
                    trailingContent = {
                        val isStravaConnected by viewModel.isStravaConnected.collectAsState()
                        if (isStravaConnected) {
                            IconButton(onClick = { viewModel.uploadToStrava(ride) }) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Upload to Strava",
                                    tint = Color(0xFFFC4C02)
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onRideClick(ride.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
