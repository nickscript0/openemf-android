package com.openemf.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.data.repository.observationEntities
import com.openemf.sensors.api.*
import com.openemf.ui.components.TechnicalObservationCard
import com.openemf.ui.components.observationText
import com.openemf.ui.model.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalsScreen(
    measurement: Measurement?,
    sensorState: SensorState,
    scanBudget: ScanBudget,
    scansEnabled: Boolean,
    onScan: () -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onSource: (SourceType, String) -> Unit,
    recordingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(SourceType.WIFI) }
    var filter by rememberSaveable { mutableStateOf("") }
    var strongestFirst by rememberSaveable { mutableStateOf(true) }
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) { delay(1000); value = SystemClock.elapsedRealtime() }
    }
    val sources = measurement?.let { it.wifiSources + it.bluetoothSources + it.cellularSources }.orEmpty()
    val observations = remember(measurement) { measurement?.observationEntities().orEmpty().associateBy { it.sourceType to it.sourceId } }
    val filtered = sources.filter { it.type == selected && (filter.isBlank() ||
        it.id.contains(filter, true) || it.name.orEmpty().contains(filter, true)) }
        .let { list -> if (strongestFirst) list.sortedWith(compareByDescending<Source> { it.rssi }.thenBy { it.id })
            else list.sortedWith(compareBy<Source> { it.name.orEmpty() }.thenBy { it.id }) }
    val scanning = sensorState is SensorState.Scanning
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("Signals") }, actions = {
            IconButton(onClick = onHistory) { Icon(Icons.Default.History, "Stored history") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Radio measurements", style = MaterialTheme.typography.headlineMedium)
                Text("Received power and quality, with Android acquisition status.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { recordingContent() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onScan, enabled = scansEnabled && !scanning) { Text(if (scanning) "Scanning…" else "Measure now") }
                    OutlinedButton(onClick = onPermissions) { Text("Permissions") }
                }
                Text("Wi-Fi budget: ${scanBudget.scansRemaining}/${ScanBudget.MAX_SCANS_PER_WINDOW} attempts · Android may throttle scans.",
                    style = MaterialTheme.typography.bodySmall)
                measurement?.let { Text("Last capture ${it.timestamp} · ${it.captureMode.name.readable()}",
                    style = MaterialTheme.typography.bodySmall) }
                if (sensorState is SensorState.Error) Text(sensorState.message, color = MaterialTheme.colorScheme.error)
                if (sensorState is SensorState.PermissionRequired) Text("Scan permissions are missing. Open Permissions to review access.",
                    color = MaterialTheme.colorScheme.error)
            }
            item {
                PrimaryTabRow(selectedTabIndex = selected.ordinal) {
                    SourceType.entries.forEach { type ->
                        Tab(selected = selected == type, onClick = { selected = type },
                            text = { Text(type.label()) })
                    }
                }
            }
            item {
                val report = measurement?.acquisition?.get(Modality.valueOf(selected.name))
                Text(report?.status?.name?.readable() ?: "No acquisition yet", style = MaterialTheme.typography.titleSmall)
                report?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (selected == SourceType.CELLULAR) Text("Serving and neighboring cells reported for the default subscription. Power metrics differ by technology.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true,
                    label = { Text("Filter name or identity") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { strongestFirst = !strongestFirst }) {
                    Text(if (strongestFirst) "Sort: strongest first" else "Sort: name")
                }
            }
            if (filtered.isEmpty()) item {
                Text(if (measurement == null) "Start a measurement to see live signals. Saved observations are available in History."
                    else if (filter.isNotBlank()) "No matching signals in this capture."
                    else "No ${selected.label()} observations in this capture. Check the acquisition status above.")
            }
            items(filtered, key = { "${it.type}:${it.id}" }) { source ->
                observations[source.type to source.id]?.let { observation ->
                    TechnicalObservationCard(observation, observationText(source.observation, now),
                        onHistory = { onSource(source.type, source.id) })
                }
            }
            if (selected == SourceType.BLUETOOTH && measurement?.pairedBluetoothDevices?.isNotEmpty() == true) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Paired inventory", style = MaterialTheme.typography.titleMedium)
                        Text("Pairing provides no received-power reading or connection state.")
                        measurement.pairedBluetoothDevices.forEach { Text("${it.name ?: "Unnamed"} · ${it.address}") }
                    }
                }
            }
        }
    }
}
