package com.openemf.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.openemf.data.database.MeasurementEntity
import com.openemf.recording.RecordingState
import com.openemf.sensors.api.*
import com.openemf.ui.model.readable
import com.openemf.ui.screens.*

enum class NavDestination(val label: String, val icon: ImageVector) {
    SIGNALS("Signals", Icons.Default.Wifi),
    DASHBOARD("Relative index", Icons.Default.Speed),
    STATISTICS("Trends", Icons.Default.BarChart)
}

@Composable
fun MainAppScaffold(
    sensorState: SensorState,
    scanBudget: ScanBudget,
    lastMeasurement: Measurement?,
    measurementHistory: List<MeasurementEntity>,
    historyError: String?,
    recordingState: RecordingState,
    recordingContent: @Composable () -> Unit,
    onStopRecording: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRequestPermissions: () -> Unit,
    onSourceClick: (SourceType, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(NavDestination.SIGNALS) }
    val recordingSection: @Composable () -> Unit = {
        var expanded by rememberSaveable { mutableStateOf(false) }
        OutlinedCard(Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (recordingState.active) "Recording: ${recordingState.phase.name.readable()} · controls"
                    else if (expanded) "Hide recording options" else "Background recording…")
            }
            if (expanded) recordingContent()
        }
    }
    Scaffold(modifier = modifier, bottomBar = {
        Column {
            if (recordingState.active) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Recording: ${recordingState.phase.name.readable()}")
                        TextButton(onClick = onStopRecording) { Text("Stop") }
                    }
                }
            }
            NavigationBar {
                NavDestination.entries.forEach { destination ->
                    NavigationBarItem(selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = { Icon(destination.icon, null) }, label = { Text(destination.label) })
                }
            }
        }
    }) { padding ->
        when (currentDestination) {
            NavDestination.SIGNALS -> SignalsScreen(lastMeasurement, sensorState, scanBudget,
                !recordingState.active, onScanClick, onRequestPermissions, onSettingsClick, onHistoryClick,
                onSourceClick, recordingSection, Modifier.padding(padding))
            NavDestination.DASHBOARD -> DashboardScreen(sensorState, scanBudget, lastMeasurement,
                onScanClick, onHistoryClick, { currentDestination = NavDestination.SIGNALS }, onSettingsClick,
                Modifier.padding(padding), recordingSection, !recordingState.active)
            NavDestination.STATISTICS -> StatisticsScreen(measurementHistory, historyError,
                onHistoryClick, onSettingsClick, Modifier.padding(padding))
        }
    }
}
