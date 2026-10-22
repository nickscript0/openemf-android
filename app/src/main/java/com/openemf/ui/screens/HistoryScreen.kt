package com.openemf.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.data.database.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val historyDateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())
private fun Instant.display() = historyDateFormat.format(this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    measurements: List<MeasurementEntity>,
    sources: List<SourceHistorySummary>,
    error: String?,
    onBackClick: () -> Unit,
    onDeleteClick: (MeasurementEntity) -> Unit,
    onMeasurementClick: (MeasurementEntity) -> Unit,
    onSourceClick: (SourceHistorySummary) -> Unit
) {
    var sourceTab by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Stored history") }, navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (sourceTab) 1 else 0) {
                Tab(selected = !sourceTab, onClick = { sourceTab = false }, text = { Text("Captures (${measurements.size})") })
                Tab(selected = sourceTab, onClick = { sourceTab = true }, text = { Text("Sources (${sources.size})") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sourceTab) {
                    item { Text("Source identity follows the observed BSSID, Bluetooth address, or cell identity. Addresses can rotate. Counts include repeated cached observations.") }
                    if (sources.isEmpty()) item { Text("No signal observations saved yet.") }
                    items(sources, key = { "${it.sourceType}:${it.sourceId}" }) { source ->
                        Card(onClick = { onSourceClick(source) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(source.name ?: source.sourceId, style = MaterialTheme.typography.titleMedium)
                                Text("${source.sourceType} · ${source.captureCount} capture appearances")
                                Text(source.sourceId, style = MaterialTheme.typography.bodySmall)
                                Text("Last captured ${source.lastCapturedAt.display()}")
                            }
                        }
                    }
                } else {
                    if (measurements.isEmpty()) item { Text("No captures saved yet. Start a scan from Live.") }
                    items(measurements, key = { it.id }) { measurement ->
                        Card(onClick = { onMeasurementClick(measurement) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(measurement.timestamp.display(), style = MaterialTheme.typography.titleMedium)
                                    Text("Wi-Fi ${measurement.wifiCount} · Bluetooth ${measurement.bluetoothCount} · Cellular ${measurement.cellularCount}")
                                    Text(if (measurement.indexAvailable == true) "Relative index ${measurement.eScore}" else "Relative index unavailable")
                                }
                                IconButton(onClick = { onDeleteClick(measurement) }) { Icon(Icons.Default.Delete, "Delete capture") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    measurement: MeasurementEntity?,
    observations: List<SignalObservationEntity>,
    acquisitions: List<RadioAcquisitionEntity>,
    error: String?,
    onBackClick: () -> Unit,
    onSourceClick: (SignalObservationEntity) -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Saved capture") }, navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(measurement?.timestamp?.display() ?: "Capture unavailable or deleted")
                Text("Freshness below describes the observation when this capture was saved.")
            }
            items(acquisitions, key = { it.modality.name }) {
                Text("${it.modality}: ${it.status}${it.detail?.let { detail -> " · $detail" } ?: ""}")
            }
            if (measurement != null && observations.isEmpty()) item { Text("No signal observations in this capture.") }
            items(observations, key = { it.rowId }) { observation ->
                SavedObservationCard(observation, onClick = { onSourceClick(observation) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHistoryScreen(
    sourceId: String,
    records: List<ObservationRecord>,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Signal history") }, navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = { TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(sourceId, style = MaterialTheme.typography.titleSmall)
                Text("${records.size} capture appearances loaded. Repeated cache reads retain their original observation time.")
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(records, key = { it.observation.rowId }) { record ->
                Column {
                    Text("Captured ${record.capturedAt.display()} · ${record.captureMode}")
                    Text("${record.acquisitionStatus}${record.acquisitionDetail?.let { " · $it" } ?: ""}")
                    SavedObservationCard(record.observation)
                }
            }
            item {
                if (loading) CircularProgressIndicator()
                else if (hasMore) TextButton(onClick = onLoadMore) { Text("Load older observations") }
                else Text(if (records.isEmpty()) "No saved observations." else "End of saved history.")
            }
        }
    }
}

@Composable
private fun SavedObservationCard(observation: SignalObservationEntity, onClick: () -> Unit = {}) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(observation.name ?: observation.sourceId, style = MaterialTheme.typography.titleMedium)
            Text("${observation.signalMetric}: ${observation.signalDbm?.let { "$it dBm" } ?: "Unavailable"}")
            Text("${observation.freshnessAtCapture} at capture${if (observation.cached) " · cached" else ""}")
            Text("Observed: ${observation.observedAt?.display() ?: "Unknown"} · ${observation.timeBasis}")
            Text("Age at capture: ${observation.ageAtCaptureMs?.let { "${it / 1000}s" } ?: "Unknown"}")
            Text(observation.sourceId, style = MaterialTheme.typography.bodySmall)
            observation.wifi?.let {
                Text("${it.frequencyMhz?.let { value -> "$value MHz" } ?: "Frequency unknown"} · channel ${it.channel ?: "unknown"} · ${it.channelWidth}")
                Text("${it.standard} · ${it.security}")
            }
            observation.bluetooth?.let { Text("${it.transport} · connection ${it.connected?.toString() ?: "unknown"}") }
            observation.cellular?.let {
                Text("${it.technology} · cell ${it.cellId ?: "unknown"} · subscription ${it.subscriptionId ?: "unknown"}")
                it.details?.let { details ->
                    Text("PLMN ${details.plmn ?: "unknown"} · bands ${details.bands.joinToString().ifEmpty { "unknown" }}")
                    if (details.nci != null) Text("NCI ${details.nci} · NR-ARFCN ${details.nrArfcn ?: "unknown"}")
                    if (details.ssRsrp != null) Text("SS-RSRP ${details.ssRsrp} dBm · SS-RSRQ ${details.ssRsrq ?: "?"} dB · SS-SINR ${details.ssSinr ?: "?"} dB")
                    if (details.csiRsrp != null) Text("CSI-RSRP ${details.csiRsrp} dBm · CSI-RSRQ ${details.csiRsrq ?: "?"} dB · CSI-SINR ${details.csiSinr ?: "?"} dB")
                    details.rsrp?.let { value -> Text("RSRP $value dBm · RSRQ ${details.rsrq ?: "?"} dB") }
                    details.rssnrDb?.let { value -> Text("RSSNR $value dB · raw ${details.rssnr}") }
                }
            }
        }
    }
}
