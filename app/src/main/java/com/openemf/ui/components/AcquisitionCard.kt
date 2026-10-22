package com.openemf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*

fun observationText(observation: Observation, nowMs: Long): String {
    val age = observation.ageMs(nowMs)?.let { "${it / 1000}s old" } ?: "age unknown"
    val freshness = observation.freshness(nowMs).name.lowercase()
    return "${if (observation.cached) "cached, " else ""}$freshness, $age"
}

fun observationDetails(observation: Observation): Map<String, String> = mapOf(
    "Observed (UTC estimate)" to (observation.timestamp?.toString() ?: "Unknown"),
    "Observation boot time" to (observation.elapsedRealtimeMs?.let { "$it ms" } ?: "Unknown"),
    "Timestamp basis" to observation.timeBasis.name.lowercase().replace('_', ' '),
    "Read from cache" to if (observation.cached) "Yes" else "No"
)

@Composable
fun AcquisitionCard(measurement: Measurement, nowMs: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Acquisition status", style = MaterialTheme.typography.titleMedium)
            Modality.entries.forEach { modality ->
                val sources: List<Source> = when (modality) {
                    Modality.WIFI -> measurement.wifiSources
                    Modality.BLUETOOTH -> measurement.bluetoothSources
                    Modality.CELLULAR -> measurement.cellularSources
                }
                val report = measurement.acquisition[modality]
                Text("${modality.name.lowercase().replaceFirstChar { it.uppercase() }}: " +
                    (report?.status?.name?.lowercase()?.replace('_', ' ') ?: "status unknown"),
                    style = MaterialTheme.typography.titleSmall)
                val fresh = sources.count { it.observation.freshness(nowMs) == Freshness.FRESH }
                val stale = sources.count { it.observation.freshness(nowMs) == Freshness.STALE }
                val unknown = sources.size - fresh - stale
                val missing = sources.count { it.rssi == null }
                Text("$fresh fresh · $stale stale · $unknown age unknown · $missing signal unavailable",
                    style = MaterialTheme.typography.bodySmall)
                report?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (measurement.pairedBluetoothDevices.isNotEmpty()) {
                HorizontalDivider()
                Text("Paired Bluetooth inventory", style = MaterialTheme.typography.titleSmall)
                Text("Pairing alone provides no signal reading or connection state.", style = MaterialTheme.typography.bodySmall)
                measurement.pairedBluetoothDevices.forEach {
                    Text("${it.name ?: "Unnamed device"} · ${it.address}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
