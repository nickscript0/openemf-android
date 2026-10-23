package com.openemf.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.data.database.MeasurementEntity
import com.openemf.ui.components.*
import com.openemf.ui.model.HistoryRange
import com.openemf.ui.model.PlotBounds
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    measurementHistory: List<MeasurementEntity>,
    historyError: String?,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.DAY) }
    val now by produceState(Instant.now()) { while (true) { delay(60_000); value = Instant.now() } }
    val until = maxOf(now, measurementHistory.map { it.timestamp }.filter { it <= Instant.now() }.maxOrNull() ?: now)
    val since = until.minusSeconds(range.seconds)
    val captures = measurementHistory.filter { it.timestamp >= since && it.timestamp <= until }
    val scored = captures.filter { it.indexAvailable }.sortedBy { it.timestamp }
    val displayed = scored.takeLast(2000)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = displayed.find { it.id == selectedId } ?: displayed.lastOrNull()
    val selectedIndex = displayed.indexOf(selected)
    val timeFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault())
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("Saved trends") }, actions = {
            IconButton(onClick = onHistoryClick) { Icon(Icons.Default.History, "Stored history") }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Durable capture history", style = MaterialTheme.typography.headlineMedium)
                Text("Relative index at capture time. For dBm and quality charts, open a source in History.")
                TextButton(onClick = onHistoryClick) { Text("Browse signal histories") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryRange.entries.forEach { choice ->
                        FilterChip(selected = choice == range, onClick = { range = choice }, label = { Text(choice.label) })
                    }
                }
            }
            historyError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("${captures.size} saved captures · ${scored.size} with an index · ${captures.size - scored.size} unavailable",
                    style = MaterialTheme.typography.titleSmall)
                if (scored.isNotEmpty()) Text("Index range ${scored.minOf { it.eScore }}–${scored.maxOf { it.eScore }} · arithmetic mean ${formatSignalValue(scored.map { it.eScore }.average())}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Relative index · 0–100", style = MaterialTheme.typography.titleMedium)
                        if (displayed.isEmpty()) Text("No scored captures in this window. Unavailable captures are not plotted as zero.")
                        else {
                            TimeScatterPlot(displayed.map { PlotSample(it.id, it.timestamp, it.eScore.toDouble()) },
                                PlotBounds(since, until, 0.0, 100.0), "index", selected?.id,
                                onSelect = { selectedId = it }, timeAxisLabel = "Capture time", autoScaled = false)
                            selected?.let { Text("${it.eScore} · ${timeFormat.format(it.timestamp)}", style = MaterialTheme.typography.titleMedium) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = selectedIndex > 0, onClick = { selectedId = displayed[selectedIndex - 1].id }) { Text("Older") }
                                OutlinedButton(enabled = selectedIndex >= 0 && selectedIndex < displayed.lastIndex,
                                    onClick = { selectedId = displayed[selectedIndex + 1].id }) { Text("Newer") }
                            }
                        }
                        if (scored.size > 2000) Text("Plot shows the latest 2,000 scored captures; counts cover the full selected window.")
                        Text("Separate dots preserve time gaps. This relative signal index is not a calibrated exposure or health threshold.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
