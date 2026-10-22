package com.openemf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.data.database.RecordingSessionSummary
import com.openemf.recording.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordingControls(
    state: RecordingState,
    previous: RecordingSessionSummary?,
    journalError: String?,
    onStart: (Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    var interval by rememberSaveable { mutableLongStateOf(60_000L) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Background recording", style = MaterialTheme.typography.titleMedium)
            Text(when (state.phase) {
                RecordingPhase.STARTING -> "Preparing recording…"
                RecordingPhase.RECORDING -> "Recording · ${state.savedCaptures} completed captures"
                RecordingPhase.PAUSING -> "Pausing…"
                RecordingPhase.PAUSED -> "Paused · ${state.savedCaptures} completed captures"
                RecordingPhase.STOPPING -> "Stopping…"
                else -> "Recording is stopped"
            })
            state.message?.let { Text(it, color = if (state.phase == RecordingPhase.ERROR)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            journalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.active) {
                Text("Radios: ${state.modalities.joinToString { it.name.lowercase() }} · target ${state.intervalMs / 1000}s")
                state.lastCaptureAt?.let { Text("Last saved ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)}") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == RecordingPhase.RECORDING) OutlinedButton(onClick = onPause) { Text("Pause") }
                    if (state.phase == RecordingPhase.PAUSED) Button(onClick = onResume) { Text("Resume") }
                    OutlinedButton(onClick = onStop, enabled = state.phase != RecordingPhase.STOPPING) { Text("Stop") }
                }
            } else {
                Text("Continues when you leave the app. Uses more battery. No coordinates are recorded.")
                Text("Target interval")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30_000L to "30s", 60_000L to "1m", 120_000L to "2m", 300_000L to "5m", 600_000L to "10m").forEach { (value, label) ->
                        FilterChip(selected = interval == value, onClick = { interval = value }, label = { Text(label) })
                    }
                }
                Button(onClick = { onStart(interval) }) { Text("Start recording") }
                previous?.let {
                    Text("Last session: ${it.session.status.name.lowercase()} · ${it.captureCount} retained captures",
                        style = MaterialTheme.typography.bodySmall)
                    it.session.reason?.let { reason -> Text(reason, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text("Android may limit scans. Cached results retain their original time. Unfiltered BLE discovery can stop with the screen off.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
