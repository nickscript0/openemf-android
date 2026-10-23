package com.openemf.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*
import com.openemf.sensors.scoring.EScoreCalculator
import com.openemf.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sensorState: SensorState,
    scanBudget: ScanBudget,
    lastMeasurement: Measurement?,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSignalsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    recordingContent: @Composable () -> Unit = {},
    scansEnabled: Boolean = true
) {
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) { delay(1000); value = SystemClock.elapsedRealtime() }
    }
    val calculator = remember { EScoreCalculator() }
    val all = lastMeasurement?.let { it.wifiSources + it.bluetoothSources + it.cellularSources }.orEmpty()
    val score = calculator.calculateFresh(all, now)
    val available = score.sourceCount > 0
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("Relative index") }, actions = {
            IconButton(onClick = onHistoryClick) { Icon(Icons.Default.History, "Stored history") }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Text("Received-signal overview", style = MaterialTheme.typography.headlineMedium)
                Text("A relative 0–100 index of fresh reported signals. It is not a calibrated exposure, dose, or safety measurement.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            item {
                if (available) {
                    EScoreGauge(score.score, "Relative index", showLegend = false, showThresholds = false)
                } else Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Index unavailable", style = MaterialTheme.typography.headlineSmall)
                        Text("Measure signals with known observation times. Stale and unknown-age readings are excluded.")
                    }
                }
            }
            item {
                Button(onClick = onScanClick, enabled = scansEnabled && sensorState !is SensorState.Scanning) {
                    Text(if (sensorState is SensorState.Scanning) "Scanning…" else "Measure now")
                }
                TextButton(onClick = onSignalsClick) { Text("View technical signals") }
                if (sensorState is SensorState.Error) Text(sensorState.message, color = MaterialTheme.colorScheme.error)
                Text("Wi-Fi scan budget: ${scanBudget.scansRemaining}/${ScanBudget.MAX_SCANS_PER_WINDOW}", style = MaterialTheme.typography.bodySmall)
            }
            if (available) item {
                SourceBreakdown(score.breakdown,
                    SourceType.entries.associateWith { type -> all.count { it.type == type && it.rssi != null && it.observation.freshness(now) == Freshness.FRESH } },
                    onSourceClick = { onSignalsClick() })
                Text("Heuristic contribution only. RSSI and cellular reference-signal power have different measurement meanings.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item { recordingContent() }
            lastMeasurement?.let { measurement ->
                item {
                    Text("Capture ${measurement.timestamp}", style = MaterialTheme.typography.bodySmall)
                    AcquisitionCard(measurement, now)
                }
            }
        }
    }
}
