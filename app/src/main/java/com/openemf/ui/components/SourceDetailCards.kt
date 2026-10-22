package com.openemf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*
import android.os.SystemClock

/**
 * Expandable card showing detailed WiFi source information.
 */
@Composable
fun WifiSourceDetailCard(
    source: WifiSource,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val exposureIndex = calculateExposureIndex(source.rssi.takeIf { source.observation.freshness(SystemClock.elapsedRealtime()) == Freshness.FRESH })
    val indexColor = getExposureIndexColor(exposureIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name ?: source.bssid,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${source.frequency} MHz • Ch ${source.channel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Exposure index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exposureIndex?.toString() ?: "Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = indexColor
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(indexColor)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    DetailRow("BSSID", source.bssid)
                    DetailRow("Signal", source.rssi?.let { "$it dBm" } ?: "Unavailable")
                    DetailRow("Band", source.band.displayName)
                    DetailRow("Channel", "${source.channel}")

                    if (source.channelWidth != WifiChannelWidth.UNKNOWN) {
                        DetailRow("Width", source.channelWidth.displayName)
                    }
                    if (source.standard != WifiStandard.UNKNOWN) {
                        DetailRow("Standard", source.standard.displayName)
                    }
                    if (source.security != WifiSecurity.UNKNOWN) {
                        DetailRow("Security", source.security.displayName)
                    }
                    if (source.isConnected) {
                        DetailRow("Status", "Connected", valueColor = Color(0xFF22C55E))
                    }
                }
            }
        }
    }
}

/**
 * Expandable card showing detailed Cellular source information.
 */
@Composable
fun CellularSourceDetailCard(
    source: CellularSource,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val exposureIndex = calculateExposureIndex(source.rssi.takeIf { source.observation.freshness(SystemClock.elapsedRealtime()) == Freshness.FRESH })
    val indexColor = getExposureIndexColor(exposureIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.name ?: "Unknown Carrier",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = source.technology.displayName + (source.band?.let { " • Band $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Exposure index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exposureIndex?.toString() ?: "Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = indexColor
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(indexColor)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    DetailRow("Signal", source.rssi?.let { "$it dBm" } ?: "Unavailable")
                    DetailRow("Technology", source.technology.displayName)
                    source.band?.let { DetailRow("Band", it.toString()) }

                    if (source.isRegistered) {
                        DetailRow("Status", "Serving Cell", valueColor = Color(0xFF22C55E))
                    }

                    // Detailed LTE/5G info
                    source.details?.let { details ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cell Identity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        details.formattedPlmn?.let { DetailRow("PLMN", it) }
                        details.mcc?.let { DetailRow("MCC", it.toString()) }
                        details.mnc?.let { DetailRow("MNC", it.toString()) }
                        details.tac?.let { DetailRow("TAC", it.toString()) }
                        details.eci?.let { DetailRow("ECI", it.toString()) }
                        details.calculatedEnb?.let { DetailRow("eNB", it.toString()) }
                        details.localCellId?.let { DetailRow("CID", it.toString()) }
                        details.pci?.let { DetailRow("PCI", it.toString()) }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Signal Quality",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        details.rsrp?.let { DetailRow("RSRP", "$it dBm") }
                        details.rsrq?.let { DetailRow("RSRQ", "$it dB") }
                        details.rssnrDb?.let { DetailRow("RSSNR", "$it dB") }
                        details.rssnr?.let { DetailRow("RSSNR (raw Android value)", it.toString()) }
                        details.nci?.let { DetailRow("NCI (36 bit)", it.toString()) }
                        details.nrArfcn?.let { DetailRow("NR-ARFCN", it.toString()) }
                        if (details.bands.isNotEmpty()) DetailRow("Reported bands", details.bands.joinToString())
                        if (source.technology == CellularTechnology.NR_5G) {
                            DetailRow("SS-RSRP", details.ssRsrp?.let { "$it dBm" } ?: "Unavailable")
                            DetailRow("SS-RSRQ", details.ssRsrq?.let { "$it dB" } ?: "Unavailable")
                            DetailRow("SS-SINR", details.ssSinr?.let { "$it dB" } ?: "Unavailable")
                            DetailRow("CSI-RSRP", details.csiRsrp?.let { "$it dBm" } ?: "Unavailable")
                            DetailRow("CSI-RSRQ", details.csiRsrq?.let { "$it dB" } ?: "Unavailable")
                            DetailRow("CSI-SINR", details.csiSinr?.let { "$it dB" } ?: "Unavailable")
                        }
                        details.cqi?.let { DetailRow("CQI", it.toString()) }
                        details.ta?.let { DetailRow("Timing Adv", it.toString()) }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        details.earfcn?.let { DetailRow("EARFCN", it.toString()) }
                        details.bandwidth?.let { DetailRow("Bandwidth", "$it MHz") }
                    }
                }
            }
        }
    }
}

/**
 * Simple row for displaying a label-value pair.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

// Extension properties for display names

val WifiBand.displayName: String
    get() = when (this) {
        WifiBand.WIFI_2_4_GHZ -> "2.4 GHz"
        WifiBand.WIFI_5_GHZ -> "5 GHz"
        WifiBand.WIFI_6_GHZ -> "6 GHz"
        WifiBand.UNKNOWN -> "Unknown"
    }

val WifiChannelWidth.displayName: String
    get() = when (this) {
        WifiChannelWidth.WIDTH_20_MHZ -> "20 MHz"
        WifiChannelWidth.WIDTH_40_MHZ -> "40 MHz"
        WifiChannelWidth.WIDTH_80_MHZ -> "80 MHz"
        WifiChannelWidth.WIDTH_160_MHZ -> "160 MHz"
        WifiChannelWidth.WIDTH_80_PLUS_80_MHZ -> "80+80 MHz"
        WifiChannelWidth.WIDTH_320_MHZ -> "320 MHz"
        WifiChannelWidth.UNKNOWN -> "Unknown"
    }

val WifiStandard.displayName: String
    get() = when (this) {
        WifiStandard.LEGACY -> "802.11a/b/g"
        WifiStandard.WIFI_4 -> "Wi-Fi 4 (802.11n)"
        WifiStandard.WIFI_5 -> "Wi-Fi 5 (802.11ac)"
        WifiStandard.WIFI_6 -> "Wi-Fi 6 (802.11ax)"
        WifiStandard.WIFI_6E -> "Wi-Fi 6E"
        WifiStandard.WIFI_7 -> "Wi-Fi 7 (802.11be)"
        WifiStandard.UNKNOWN -> "Unknown"
    }

val WifiSecurity.displayName: String
    get() = when (this) {
        WifiSecurity.OPEN -> "Open"
        WifiSecurity.WEP -> "WEP"
        WifiSecurity.WPA -> "WPA"
        WifiSecurity.WPA2 -> "WPA2"
        WifiSecurity.WPA3 -> "WPA3"
        WifiSecurity.WPA2_WPA3 -> "WPA2/WPA3"
        WifiSecurity.UNKNOWN -> "Unknown"
    }

val CellularTechnology.displayName: String
    get() = when (this) {
        CellularTechnology.GSM_2G -> "2G (GSM)"
        CellularTechnology.CDMA_2G -> "2G (CDMA)"
        CellularTechnology.UMTS_3G -> "3G (UMTS)"
        CellularTechnology.LTE_4G -> "4G (LTE)"
        CellularTechnology.NR_5G -> "5G (NR)"
        CellularTechnology.UNKNOWN -> "Unknown"
    }
