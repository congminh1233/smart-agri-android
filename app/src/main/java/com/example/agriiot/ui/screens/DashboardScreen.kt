package com.example.agriiot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.agriiot.data.local.ScheduleEntity
import com.example.agriiot.data.model.Actuator
import com.example.agriiot.data.model.EventItem
import com.example.agriiot.data.model.Telemetry
import com.example.agriiot.viewmodel.UiState
import com.example.agriiot.viewmodel.ZoneViewModel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ZoneViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isManualLoading by viewModel.isManualLoading.collectAsState()
    val availableZones by viewModel.availableZones.collectAsState()
    val selectedZoneId by viewModel.selectedZoneId.collectAsState()
    val events by viewModel.events.collectAsState()
    val localSchedules by viewModel.localSchedules.collectAsState()

    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    var expanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Control Panel", "Event History", "Schedules")

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startPolling()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Smart Agriculture Dashboard") })
                
                // Zone Selector moved here to be shared across all tabs
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedZoneId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Zone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableZones.forEach { zone ->
                            DropdownMenuItem(
                                text = { Text(zone) },
                                onClick = {
                                    viewModel.selectZone(zone)
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ControlTab(
                    uiState = uiState,
                    isRefreshing = isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    isManualLoading = isManualLoading,
                    viewModel = viewModel,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.refresh()
                            isRefreshing = false
                        }
                    }
                )
                1 -> EventTab(events = events)
                2 -> ScheduleTab(
                    schedules = localSchedules,
                    onAdd = { zone, device, target, h, m -> viewModel.addSchedule(zone, device, target, h, m) },
                    onDelete = { viewModel.deleteSchedule(it) },
                    onUpdate = { schedule, target -> viewModel.updateSchedule(schedule, target) },
                    onToggle = { viewModel.toggleScheduleStatus(it) },
                    availableZones = availableZones
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlTab(
    uiState: UiState,
    isRefreshing: Boolean,
    pullToRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    isManualLoading: Boolean,
    viewModel: ZoneViewModel,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text("Telemetry", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    zoneData.telemetry?.let {
                        TelemetryGrid(it)
                    } ?: Text("Telemetry data unavailable")

                    zoneData.actuator?.let { actuator ->
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        ActuatorControls(
                            actuator = actuator,
                            waterLevel = zoneData.telemetry?.waterLevel ?: 0f,
                            onCommand = { target, action -> viewModel.sendCommand(target, action) },
                            isLoading = isManualLoading
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            is UiState.Error -> {
                ErrorView(message = state.message, onRetry = { viewModel.refresh() })
            }
        }
    }
}

@Composable
fun EventTab(events: List<EventItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            val isCritical = event.severity == "critical" || event.eventType == "water_tank_low"
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCritical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCritical) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isCritical) Color.Red else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "[${event.zoneId}] ${event.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCritical) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTimestamp(event.timestamp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(isoString: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
        zonedDateTime.format(formatter)
    } catch (e: Exception) {
        isoString
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTab(
    schedules: List<ScheduleEntity>,
    onAdd: (String, String, String, Int, Int) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onUpdate: (ScheduleEntity, String) -> Unit,
    onToggle: (ScheduleEntity) -> Unit,
    availableZones: List<String>
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntity?>(null) }
    
    var selectedZone by remember { mutableStateOf(availableZones.firstOrNull() ?: "zone-01") }
    var selectedDeviceName by remember { mutableStateOf("Water Pump") }
    var selectedTarget by remember { mutableStateOf("water_pump") }
    
    val deviceOptions = listOf(
        Triple("Water Pump", "water_pump", Icons.Default.Water),
        Triple("Fan", "fan", Icons.Default.Air),
        Triple("Grow Light", "grow_light", Icons.Default.Lightbulb)
    )

    var zoneExpanded by remember { mutableStateOf(false) }
    var deviceExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Create New Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Zone Selector in New Schedule Form
                ExposedDropdownMenuBox(
                    expanded = zoneExpanded,
                    onExpandedChange = { zoneExpanded = !zoneExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedZone,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Zone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                        availableZones.forEach { zone ->
                            DropdownMenuItem(text = { Text(zone) }, onClick = { selectedZone = zone; zoneExpanded = false })
                        }
                    }
                }

                // Device Selector
                ExposedDropdownMenuBox(
                    expanded = deviceExpanded,
                    onExpandedChange = { deviceExpanded = !deviceExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDeviceName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = deviceExpanded, onDismissRequest = { deviceExpanded = false }) {
                        deviceOptions.forEach { (name, target, icon) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                leadingIcon = { Icon(icon, null) },
                                onClick = { 
                                    selectedDeviceName = name
                                    selectedTarget = target
                                    deviceExpanded = false 
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { editingSchedule = null; showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Timer, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Time & Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Active Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (schedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No schedules created", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(schedules) { schedule ->
                    ScheduleItem(
                        schedule = schedule, 
                        onToggle = onToggle, 
                        onDelete = onDelete,
                        onEdit = { 
                            editingSchedule = it
                            selectedZone = it.zoneId
                            selectedDeviceName = it.deviceName
                            selectedTarget = deviceOptions.find { opt -> opt.first == it.deviceName }?.second ?: "unknown"
                            showTimePicker = true 
                        }
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = editingSchedule?.hour ?: 12,
            initialMinute = editingSchedule?.minute ?: 0
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (editingSchedule != null) "Edit Schedule" else "Pick Time", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (editingSchedule != null) {
                                onUpdate(
                                    editingSchedule!!.copy(
                                        zoneId = selectedZone,
                                        deviceName = selectedDeviceName,
                                        hour = timePickerState.hour,
                                        minute = timePickerState.minute,
                                        isActive = true
                                    ),
                                    selectedTarget
                                )
                            } else {
                                onAdd(selectedZone, selectedDeviceName, selectedTarget, timePickerState.hour, timePickerState.minute)
                            }
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(
    schedule: ScheduleEntity,
    onToggle: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onEdit: (ScheduleEntity) -> Unit
) {
    val icon = when (schedule.deviceName) {
        "Water Pump" -> Icons.Default.Water
        "Fan" -> Icons.Default.Air
        else -> Icons.Default.Lightbulb
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.deviceName, fontWeight = FontWeight.Bold)
                Text("Zone: ${schedule.zoneId}", style = MaterialTheme.typography.bodySmall)
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", schedule.hour, schedule.minute),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            IconButton(onClick = { onEdit(schedule) }) {
                Icon(Icons.Default.Edit, null)
            }
            Switch(checked = schedule.isActive, onCheckedChange = { onToggle(schedule) })
            IconButton(onClick = { onDelete(schedule) }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error: $message",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
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
    actuator: Actuator,
    waterLevel: Float,
    onCommand: (String, String) -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val controls = listOf(
        ControlItem("Water Pump", "water_pump", actuator.waterPump ?: "OFF", Icons.Default.Water),
        ControlItem("Fan", "fan", actuator.fan ?: "OFF", Icons.Default.Air),
        ControlItem("Grow Light", "grow_light", actuator.growLight ?: "OFF", Icons.Default.Lightbulb)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(300.dp)
    ) {
        items(controls) { item ->
            val isOn = item.status.lowercase() == "on"
            val isPump = item.target == "water_pump"
            val isLowWater = isPump && waterLevel < 15f
            
            val backgroundColor = when {
                isLowWater -> Color.Red
                isOn -> Color(0xFF4CAF50)
                else -> Color.LightGray
            }
            
            val contentColor = if (isLowWater || isOn) Color.White else Color.Black

            Card(
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                onClick = {
                    if (!isLoading) {
                        if (isPump && waterLevel < 15f) {
                            Toast.makeText(context, "Mực nước trong bể chứa thấp, bơm sẽ bị khoá an toàn!", Toast.LENGTH_SHORT).show()
                        } else {
                            onCommand(item.target, if (isOn) "off" else "on")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = null, tint = contentColor)
                    Text(
                        text = item.label, 
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                    Text(
                        text = item.status.uppercase(), 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
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

data class ControlItem(
    val label: String,
    val target: String,
    val status: String,
    val icon: ImageVector
)
