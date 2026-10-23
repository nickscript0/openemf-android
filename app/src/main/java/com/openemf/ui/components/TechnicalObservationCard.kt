package com.openemf.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openemf.data.database.SignalObservationEntity
import com.openemf.ui.model.*

@Composable
fun TechnicalObservationCard(
    observation: SignalObservationEntity,
    freshnessLabel: String,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null,
    compact: Boolean = false
) {
    var expanded by rememberSaveable(observation.sourceId) { mutableStateOf(false) }
    val rangeWarning = if (observation.signalDbm != null && observation.measurablePower() == null)
        "Android reported ${observation.signalDbm} dBm, outside this radio's expected range; excluded from the index and signal chart."
        else null
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (compact) {
            Column(Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Technical details") { expanded = !expanded }
                .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(observation.name?.takeIf { it.isNotBlank() } ?: observation.sourceId,
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(observation.powerText(), style = MaterialTheme.typography.labelMedium)
                    Text(freshnessLabel,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 120.dp))
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(listOfNotNull(observation.metricLabel(), observation.sourceId, observation.radioSummary()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                rangeWarning?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!compact || expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            rangeWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            SelectionContainer {
                Text(observation.sourceId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            observation.radioSummary()?.let { Text(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compact) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Technical details") }
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

private fun SignalObservationEntity.radioSummary(): String? =
    wifi?.let { "${it.frequencyMhz?.let { value -> "$value MHz" } ?: "Frequency unavailable"} · channel ${it.channel ?: "unknown"}" }
        ?: bluetooth?.let { "${it.transport} · connection ${if (it.connected == true) "connected" else "unknown"}" }
        ?: cellular?.let { "${it.technology.name.replace('_', ' ')} · ${if (it.registered) "serving cell" else "not registered"}" }
