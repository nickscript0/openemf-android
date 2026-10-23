package com.openemf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.ui.model.*
import com.openemf.ui.viewmodel.SourceChartState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SignalHistoryChart(state: SourceChartState, onRange: (HistoryRange) -> Unit, modifier: Modifier = Modifier) {
    val metrics = remember(state.records) { SignalMetric.entries.filter { metric -> state.records.any { metric.value(it) != null } } }
    var metricName by rememberSaveable { mutableStateOf<String?>(null) }
    val primaryMetric = state.records.firstOrNull()?.observation?.signalMetric
    val metric = metrics.find { it.name == metricName } ?: metrics.find { it.name == primaryMetric } ?: metrics.firstOrNull() ?: SignalMetric.RSSI
    val series = remember(state.records, metric, state.since, state.until) {
        signalSeries(state.records, metric, state.since, state.until)
    }
    var selectedRow by remember(metric) { mutableStateOf<Long?>(null) }
    val selected = series.points.find { it.record.observation.rowId == selectedRow } ?: series.points.lastOrNull()
    val selectedIndex = series.points.indexOf(selected)
    val timeFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Signal history chart", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryRange.entries.forEach { range ->
                    FilterChip(selected = state.range == range, onClick = { onRange(range) }, label = { Text(range.label) })
                }
            }
            if (metrics.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEach { choice ->
                    FilterChip(selected = choice == metric, onClick = { metricName = choice.name }, label = { Text(choice.label) })
                }
            }
            Text("${metric.label} · ${metric.unit}", style = MaterialTheme.typography.titleMedium)
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                series.points.isEmpty() -> Text("No fresh, timestamped ${metric.label} samples in this window. Raw appearances remain below.")
                else -> {
                    val bounds = remember(series, state.since, state.until) { plotBounds(series.points, state.since, state.until) }
                    TimeScatterPlot(series.points.map { PlotSample(it.record.observation.rowId.toString(), it.time, it.value) },
                        bounds, metric.unit, selected?.record?.observation?.rowId?.toString(),
                        onSelect = { selectedRow = it.toLong() })
                    selected?.let {
                        Text("${formatSignalValue(it.value)} ${metric.unit} · ${timeFormat.format(it.time)}",
                            style = MaterialTheme.typography.titleSmall)
                        Text("${it.record.observation.timeBasis.name.readable()} timestamp · ${it.record.captureMode.name.readable()}" +
                            if (it.record.observation.cached) " · read from cache" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = selectedIndex > 0, onClick = { selectedRow = series.points[selectedIndex - 1].record.observation.rowId }) { Text("Older") }
                        OutlinedButton(enabled = selectedIndex >= 0 && selectedIndex < series.points.lastIndex,
                            onClick = { selectedRow = series.points[selectedIndex + 1].record.observation.rowId }) { Text("Newer") }
                    }
                    Text("${series.points.size} unique samples · ${series.repeatedAppearances} repeated reads collapsed · ${series.excludedAppearances} excluded appearances",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.truncated) Text("Chart limited to the 2,000 most recent capture appearances in this window. Choose a shorter window or export raw CSV.",
                style = MaterialTheme.typography.bodySmall)
            Text("Dots show fresh-at-capture numeric observations at their original time. Missing, stale and unknown-time values are excluded. Dots are not connected across gaps; raw rows are retained.",
                style = MaterialTheme.typography.bodySmall)
            Text("Hardware wall times are estimates. Event receipt marks when Android delivered a reading.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
