package com.example.agriiot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.agriiot.data.model.Telemetry
import com.example.agriiot.viewmodel.UiState
import com.example.agriiot.viewmodel.ZoneViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ZoneViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isManualLoading by viewModel.isManualLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Polling lifecycle management
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startPolling()
        }
    }

    // Critical Event Listener
    LaunchedEffect(Unit) {
        viewModel.criticalEvent.collect { event ->
            snackbarHostState.showSnackbar(
                message = "CRITICAL: ${event.description}",
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Snackbar(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                snackbarData = data
            )
        } },
        topBar = {
            TopAppBar(title = { Text("Smart Agriculture Dashboard") })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refresh()
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Success -> {
                    val zoneData = state.data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text("Telemetry", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TelemetryGrid(zoneData.telemetry)

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Controls", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        ActuatorControls(
                            waterPump = zoneData.actuator.waterPump,
                            fan = zoneData.actuator.fan,
                            growLight = zoneData.actuator.growLight,
                            onCommand = { target, action -> viewModel.sendCommand(target, action) },
                            onSchedule = { target, mins -> viewModel.scheduleDeviceOff(target, mins) },
                            isLoading = isManualLoading
                        )
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryGrid(telemetry: Telemetry) {
    val items = listOf(
        TelemetryItem("Air Temp", "${telemetry.airTemperature}°C", Icons.Default.Thermostat, Color.Blue),
        TelemetryItem("Soil Moisture", "${telemetry.soilMoisture}%", Icons.Default.WaterDrop, Color.Cyan),
        TelemetryItem("Water Level", "${telemetry.waterLevel}%", Icons.Default.Waves, if (telemetry.waterLevel < 15f) Color.Red else Color.Green),
        TelemetryItem("Light", "${telemetry.light} Lux", Icons.Default.LightMode, Color.Yellow)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(250.dp)
    ) {
        items(items) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = item.color.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = null, tint = item.color)
                    Text(item.label, style = MaterialTheme.typography.labelMedium)
                    Text(item.value, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
fun ActuatorControls(
    waterPump: String,
    fan: String,
    growLight: String,
    onCommand: (String, String) -> Unit,
    onSchedule: (String, Long) -> Unit,
    isLoading: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlRow("Water Pump", "water_pump", waterPump, onCommand, onSchedule, isLoading)
        ControlRow("Fan", "fan", fan, onCommand, onSchedule, isLoading)
        ControlRow("Grow Light", "grow_light", growLight, onCommand, onSchedule, isLoading)
    }
}

@Composable
fun ControlRow(
    label: String,
    target: String,
    status: String,
    onCommand: (String, String) -> Unit,
    onSchedule: (String, Long) -> Unit,
    isLoading: Boolean
) {
    val isOn = status.lowercase() == "on"
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text("Status: ${status.uppercase()}", style = MaterialTheme.typography.bodySmall)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOn) {
                    TextButton(onClick = { onSchedule(target, 15) }, enabled = !isLoading) {
                        Text("15m Timer")
                    }
                }
                Switch(
                    checked = isOn,
                    onCheckedChange = { onCommand(target, if (it) "on" else "off") },
                    enabled = !isLoading
                )
            }
        }
    }
}

data class TelemetryItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)
