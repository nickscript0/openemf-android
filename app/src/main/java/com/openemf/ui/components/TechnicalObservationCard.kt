package com.openemf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.openemf.data.database.SignalObservationEntity
import com.openemf.ui.model.*

@Composable
fun TechnicalObservationCard(
    observation: SignalObservationEntity,
    freshnessLabel: String,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null
) {
    var expanded by rememberSaveable(observation.sourceId) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(observation.name?.takeIf { it.isNotBlank() } ?: observation.sourceType.label(),
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(observation.powerText(), style = MaterialTheme.typography.headlineSmall)
                    Text(observation.metricLabel(), style = MaterialTheme.typography.labelMedium)
                }
                Text(freshnessLabel, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(max = 180.dp))
            }
            if (observation.signalDbm != null && observation.measurablePower() == null) {
                Text("Android reported ${observation.signalDbm} dBm, outside this radio's expected range; excluded from the index and signal chart.",
                    style = MaterialTheme.typography.bodySmall)
            }
            SelectionContainer {
                Text(observation.sourceId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            observation.wifi?.let { Text("${it.frequencyMhz?.let { value -> "$value MHz" } ?: "Frequency unavailable"} · channel ${it.channel ?: "unknown"}") }
            observation.bluetooth?.let { Text("${it.transport} · connection ${if (it.connected == true) "connected" else "unknown"}") }
            observation.cellular?.let { Text("${it.technology.name.replace('_', ' ')} · ${if (it.registered) "serving cell" else "not registered"}") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Technical details") }
                onHistory?.let { TextButton(onClick = it) { Text("Signal history") } }
            }
            if (expanded) {
                HorizontalDivider()
                val fields = observation.technicalFields() + listOf(
                    "Observed (UTC estimate)" to (observation.observedAt?.toString() ?: "Unknown"),
                    "Timestamp basis" to observation.timeBasis.name.readable(),
                    "Age when captured" to (observation.ageAtCaptureMs?.let { "$it ms" } ?: "Unknown"),
                    "Observation boot time" to (observation.observedElapsedRealtimeMs?.let { "$it ms" } ?: "Unknown"),
                    "Read from cache" to if (observation.cached) "Yes" else "No")
                fields.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
