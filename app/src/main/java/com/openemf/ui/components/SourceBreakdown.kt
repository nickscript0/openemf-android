package com.openemf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.SourceType
import com.openemf.ui.theme.BluetoothColor
import com.openemf.ui.theme.CellularColor
import com.openemf.ui.theme.WiFiColor
import com.openemf.ui.theme.getEScoreColor

/**
 * Shows breakdown of E-Score by source type.
 */
@Composable
fun SourceBreakdown(
    breakdown: Map<SourceType, Int>,
    sourceCounts: Map<SourceType, Int>,
    modifier: Modifier = Modifier,
    onSourceClick: ((SourceType) -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Source Breakdown",
                style = MaterialTheme.typography.titleMedium
            )

            // WiFi
            SourceRow(
                icon = Icons.Default.Wifi,
                label = "WiFi",
                color = WiFiColor,
                percentage = breakdown[SourceType.WIFI] ?: 0,
                count = sourceCounts[SourceType.WIFI] ?: 0,
                onClick = onSourceClick?.let { { it(SourceType.WIFI) } }
            )

            // Bluetooth
            SourceRow(
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                color = BluetoothColor,
                percentage = breakdown[SourceType.BLUETOOTH] ?: 0,
                count = sourceCounts[SourceType.BLUETOOTH] ?: 0,
                onClick = onSourceClick?.let { { it(SourceType.BLUETOOTH) } }
            )

            // Cellular
            SourceRow(
                icon = Icons.Default.CellTower,
                label = "Cellular",
                color = CellularColor,
                percentage = breakdown[SourceType.CELLULAR] ?: 0,
                count = sourceCounts[SourceType.CELLULAR] ?: 0,
                onClick = onSourceClick?.let { { it(SourceType.CELLULAR) } }
            )
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    label: String,
    color: Color,
    percentage: Int,
    count: Int,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )

        // Label and count
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$count fresh readings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Percentage bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage / 100f)
                    .background(color)
            )
        }

        // Percentage text - show "< 1%" for tiny but non-zero contributions
        Text(
            text = if (percentage == 0 && count > 0) "< 1%" else "${percentage}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(40.dp)
        )
    }
}

/**
 * Expandable list of individual sources.
 */
@Composable
fun SourceList(
    title: String,
    icon: ImageVector,
    color: Color,
    sources: List<SourceItem>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${sources.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            // Source list
            if (expanded) {
                HorizontalDivider()
                sources.forEach { source ->
                    SourceItemRow(source = source, accentColor = color)
                }
            }
        }
    }
}

/**
 * Inline source display showing all sources in a compact, unified list.
 * Shows sources grouped by type inline, with expandable details per source.
 */
@Composable
fun InlineSourceList(
    wifiSources: List<SourceItem>,
    bluetoothSources: List<SourceItem>,
    cellularSources: List<SourceItem>,
    modifier: Modifier = Modifier
) {
    val allSources = mutableListOf<Pair<SourceItem, Color>>()
    wifiSources.forEach { allSources.add(it to WiFiColor) }
    bluetoothSources.forEach { allSources.add(it to BluetoothColor) }
    cellularSources.forEach { allSources.add(it to CellularColor) }

    // Sort by exposure index descending (highest first)
    val sortedSources = allSources.sortedByDescending { it.first.exposureIndex }

    if (sortedSources.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            sortedSources.forEach { (source, color) ->
                InlineSourceItem(source = source, accentColor = color)
            }
        }
    }
}

@Composable
private fun InlineSourceItem(
    source: SourceItem,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val indexColor = getExposureIndexColor(source.exposureIndex)
    val hasDetails = source.details.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color indicator dot for source type
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )

            // Name and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                source.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Exposure index with color
            Text(
                text = source.rssi?.let { "$it dBm" } ?: "Unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = indexColor
            )

            // Color dot for exposure level
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(indexColor)
            )

            // Expand indicator
            if (hasDetails) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expanded details
        AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 16.dp, bottom = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                source.details.forEach { (label, value) ->
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
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItemRow(
    source: SourceItem,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val indexColor = getExposureIndexColor(source.exposureIndex)
    val hasDetails = source.details.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium
                )
                source.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Exposure index with color indicator (like ElectroSmart)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = source.rssi?.let { "$it dBm" } ?: "Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = indexColor
                )
                // Color dot indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(indexColor)
                )
                // Expand indicator if has details
                if (hasDetails) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Expanded details section
        AnimatedVisibility(
            visible = expanded && hasDetails,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                source.details.forEach { (label, value) ->
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
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

data class SourceItem(
    val name: String?,
    val subtitle: String?,
    val rssi: Int?,
    val contributionPercent: Int,
    val exposureIndex: Int? = null,  // Per-source exposure index (0-100)
    // Extended details for Phase 3 (optional)
    val details: Map<String, String> = emptyMap()
)

/**
 * Calculate per-source exposure index using ElectroSmart formula.
 * Same formula as aggregate score, applied to individual RSSI.
 */
fun calculateExposureIndex(rssiDbm: Int?): Int? {
    if (rssiDbm == null) return null
    return when {
        rssiDbm < -140 -> 0
        rssiDbm > 10 -> 100
        else -> kotlin.math.floor(2.0 * (rssiDbm + 140) / 3.0).toInt().coerceIn(0, 100)
    }
}

/**
 * Get color for exposure index (uses 6-level thresholds).
 */
fun getExposureIndexColor(index: Int?): Color = index?.let { getEScoreColor(it) } ?: Color.Gray
