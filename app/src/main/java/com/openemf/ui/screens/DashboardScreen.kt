package com.openemf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import com.openemf.ui.theme.BluetoothColor
import com.openemf.ui.theme.CellularColor
import com.openemf.ui.theme.WiFiColor
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*
import android.os.SystemClock
import kotlinx.coroutines.delay
import com.openemf.sensors.scoring.EScoreCalculator
import com.openemf.ui.components.*

/**
 * Main dashboard screen showing E-Score and source breakdown.
 * Wave animation shows during scanning, then displays results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    sensorState: SensorState,
    scanBudget: ScanBudget,
    lastMeasurement: Measurement?,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSolutionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isScanning = sensorState is SensorState.Scanning
    val nowElapsedMs by produceState(SystemClock.elapsedRealtime()) {
        while (true) { delay(1000); value = SystemClock.elapsedRealtime() }
    }
    val calculator = remember { EScoreCalculator() }
    val currentScore = lastMeasurement?.let {
        calculator.calculateFresh(it.wifiSources + it.bluetoothSources + it.cellularSources, nowElapsedMs)
    }
    val indexAvailable = (currentScore?.sourceCount ?: 0) > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenEMF") },
                actions = {
                    // Small scan button with tooltip
                    IconButton(
                        onClick = onScanClick,
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = "Measure Now",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = onScanClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // E-Score display
                val score = currentScore?.score ?: 0
            val label = "Relative received-signal index"

            // Calculate intensities for wave visualization
            val wifiIntensity = lastMeasurement?.wifiSources?.let {
                calculateAggregateIntensity(it.mapNotNull { s -> s.rssi })
            } ?: 0.3f
            val bluetoothIntensity = lastMeasurement?.bluetoothSources?.let {
                calculateAggregateIntensity(it.mapNotNull { s -> s.rssi })
            } ?: 0.2f
            val cellularIntensity = lastMeasurement?.cellularSources?.let {
                calculateAggregateIntensity(it.mapNotNull { s -> s.rssi })
            } ?: 0.4f

            // Show wave animation ONLY during scanning
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EMFWaveVisualization(
                        wifiIntensity = wifiIntensity,
                        bluetoothIntensity = bluetoothIntensity,
                        cellularIntensity = cellularIntensity,
                        overallScore = score,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp)
                    )
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Show gauge when NOT scanning
            AnimatedVisibility(
                visible = !isScanning && indexAvailable,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EScoreGauge(
                    score = score,
                    label = label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    onLabelClick = onSolutionsClick
                )
            }

            if (!isScanning && lastMeasurement != null && !indexAvailable) {
                Text("Relative index unavailable: no fresh measurable signals", style = MaterialTheme.typography.bodyLarge)
            }
            Text("Relative received signal, not a calibrated exposure or safety measurement.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // State message (errors, throttling, etc.)
            StateMessage(state = sensorState)

            // Scan budget indicator (compact)
            if (!isScanning && scanBudget.scansRemaining < ScanBudget.MAX_SCANS_PER_WINDOW) {
                Text(
                    text = "${scanBudget.scansRemaining}/${ScanBudget.MAX_SCANS_PER_WINDOW} scans remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Source contributions with expandable details
            lastMeasurement?.let { measurement ->
                val breakdown = currentScore?.breakdown.orEmpty()
                var showDetails by remember { mutableStateOf(false) }

                // Contribution summary card with toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        // Header row with toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDetails = !showDetails }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source Contributions",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDetails) "Collapse" else "Expand"
                            )
                        }

                        // Contribution bars (always visible)
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ContributionBar(
                                icon = Icons.Default.Wifi,
                                label = "Wi-Fi",
                                color = WiFiColor,
                                percentage = breakdown[SourceType.WIFI] ?: 0,
                                count = measurement.wifiSources.size
                            )
                            ContributionBar(
                                icon = Icons.Default.Bluetooth,
                                label = "Bluetooth",
                                color = BluetoothColor,
                                percentage = breakdown[SourceType.BLUETOOTH] ?: 0,
                                count = measurement.bluetoothSources.size
                            )
                            ContributionBar(
                                icon = Icons.Default.CellTower,
                                label = "Cellular",
                                color = CellularColor,
                                percentage = breakdown[SourceType.CELLULAR] ?: 0,
                                count = measurement.cellularSources.size
                            )
                        }

                        // Expandable detailed source lists
                        AnimatedVisibility(
                            visible = showDetails,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // WiFi sources
                                if (measurement.wifiSources.isNotEmpty()) {
                                    SourceList(
                                        title = "Wi-Fi",
                                        icon = Icons.Default.Wifi,
                                        color = WiFiColor,
                                        sources = measurement.wifiSources
                                            .sortedByDescending { it.rssi }
                                            .map { source ->
                                                SourceItem(
                                                    name = source.name ?: source.bssid,
                                                    subtitle = "${source.frequency} MHz • Ch ${source.channel.takeIf { it != 0 } ?: "Unknown"} • ${observationText(source.observation, nowElapsedMs)}",
                                                    rssi = source.rssi,
                                                    contributionPercent = source.contributionPercent,
                                                    exposureIndex = calculateExposureIndex(source.rssi.takeIf { source.observation.freshness(nowElapsedMs) == Freshness.FRESH }),
                                                    details = buildWifiDetails(source)
                                                )
                                            }
                                    )
                                }

                                // Bluetooth sources
                                if (measurement.bluetoothSources.isNotEmpty()) {
                                    SourceList(
                                        title = "Bluetooth",
                                        icon = Icons.Default.Bluetooth,
                                        color = BluetoothColor,
                                        sources = measurement.bluetoothSources
                                            .sortedByDescending { it.rssi }
                                            .map { source ->
                                                SourceItem(
                                                    name = source.name ?: "Unknown Device",
                                                    subtitle = "${source.transport} • ${observationText(source.observation, nowElapsedMs)}",
                                                    rssi = source.rssi,
                                                    contributionPercent = source.contributionPercent,
                                                    exposureIndex = calculateExposureIndex(source.rssi.takeIf { source.observation.freshness(nowElapsedMs) == Freshness.FRESH }),
                                                    details = buildBluetoothDetails(source)
                                                )
                                            }
                                    )
                                }

                                // Cellular sources
                                if (measurement.cellularSources.isNotEmpty()) {
                                    SourceList(
                                        title = "Cellular",
                                        icon = Icons.Default.CellTower,
                                        color = CellularColor,
                                        sources = measurement.cellularSources
                                            .sortedByDescending { it.rssi }
                                            .map { source ->
                                                SourceItem(
                                                    name = source.name ?: "Unknown Carrier",
                                                    subtitle = "${source.technology.displayName} • ${observationText(source.observation, nowElapsedMs)}",
                                                    rssi = source.rssi,
                                                    contributionPercent = source.contributionPercent,
                                                    exposureIndex = calculateExposureIndex(source.rssi.takeIf { source.observation.freshness(nowElapsedMs) == Freshness.FRESH }),
                                                    details = buildCellularDetails(source)
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Magnetic field (ELF-EMF)
                measurement.magneticField?.let { magnetic ->
                    MagneticFieldCard(magnetic = magnetic)
                }

                // Confidence indicator
                AcquisitionCard(measurement, nowElapsedMs)
            }

            // Empty state
            if (lastMeasurement == null && sensorState !is SensorState.Scanning) {
                EmptyState()
            }
        }
        }
    }
}

@Composable
private fun StateMessage(state: SensorState) {
    val message = when (state) {
        is SensorState.Idle -> null
        is SensorState.Scanning -> "Scanning..."
        is SensorState.Complete -> "Scan complete (${state.durationMs}ms)"
        is SensorState.ThrottleLimited -> "Throttled - using cached data"
        is SensorState.PermissionRequired -> "Permissions required"
        is SensorState.Error -> state.message
    }

    val isError = state is SensorState.Error || state is SensorState.PermissionRequired

    message?.let {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun ConfidenceCard(confidence: ConfidenceFlags) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Data Confidence",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ConfidenceItem("WiFi", confidence.wifi)
                ConfidenceItem("Bluetooth", confidence.bluetooth)
                ConfidenceItem("Cellular", confidence.cellular)
            }
        }
    }
}

@Composable
private fun ConfidenceItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Sensors,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No measurements yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the sensor icon in the top bar to measure EMF exposure",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MagneticFieldCard(magnetic: MagneticFieldReading) {
    val severityColor = when (magnetic.severityLevel) {
        MagneticSeverity.NORMAL -> MaterialTheme.colorScheme.primary
        MagneticSeverity.SLIGHTLY_ELEVATED -> MaterialTheme.colorScheme.secondary
        MagneticSeverity.ELEVATED -> MaterialTheme.colorScheme.tertiary
        MagneticSeverity.HIGH -> MaterialTheme.colorScheme.error
        MagneticSeverity.VERY_HIGH -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.ElectricalServices,
                    contentDescription = null,
                    tint = severityColor
                )
                Text(
                    text = "Magnetic Field (ELF)",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = String.format("%.1f µT", magnetic.magnitudeUt),
                        style = MaterialTheme.typography.headlineMedium,
                        color = severityColor
                    )
                    Text(
                        text = String.format("%.1f mG", magnetic.magnitudeMg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = magnetic.severityLevel.name.replace("_", " "),
                        style = MaterialTheme.typography.labelLarge,
                        color = severityColor
                    )
                    if (magnetic.estimatedAcComponentUt > 1.0) {
                        Text(
                            text = String.format("~%.1f µT AC", magnetic.estimatedAcComponentUt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Info text
            Text(
                text = "Low-frequency fields from power lines & appliances",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Score labels with 6-level thresholds
private fun getScoreLabel(score: Int): String = when {
    score <= 10 -> "Excellent"  // Very low exposure
    score <= 25 -> "Good"       // Low exposure
    score <= 50 -> "Moderate"   // Typical urban levels
    score <= 75 -> "Elevated"   // Above average
    score <= 90 -> "High"       // Consider reducing sources
    else -> "Very High"         // Critical
}

/**
 * Build detail map for WiFi sources.
 * Shows BSSID, signal, band, channel width, standard, and security.
 */
private fun buildWifiDetails(source: WifiSource): Map<String, String> {
    val details = observationDetails(source.observation).toMutableMap()

    details["BSSID"] = source.bssid
    details["Signal"] = source.rssi?.let { "$it dBm" } ?: "Unavailable"
    details["Band"] = source.band.displayName
    details["Channel"] = source.channel.takeIf { it != 0 }?.toString() ?: "Unknown"

    if (source.channelWidth != WifiChannelWidth.UNKNOWN) {
        details["Width"] = source.channelWidth.displayName
    }
    if (source.standard != WifiStandard.UNKNOWN) {
        details["Standard"] = source.standard.displayName
    }
    if (source.security != WifiSecurity.UNKNOWN) {
        details["Security"] = source.security.displayName
    }
    if (source.isConnected) {
        details["Status"] = "Connected"
    }

    return details
}

/**
 * Build detail map for Bluetooth sources.
 * Shows address, signal, device type, and connection status.
 */
private fun buildBluetoothDetails(source: BluetoothSource): Map<String, String> {
    val details = observationDetails(source.observation).toMutableMap()

    details["Address"] = source.address
    details["Transport"] = source.transport.name
    details["Connection"] = "Unknown (discovery only)"
    details["Signal"] = source.rssi?.let { "$it dBm" } ?: "Unavailable"
    details["Type"] = source.deviceType.name.lowercase()
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    if (source.isConnected) {
        details["Status"] = "Connected"
    }

    return details
}

/**
 * Build detail map for Cellular sources.
 * Shows technology, band, and detailed LTE/5G info when available.
 */
private fun buildCellularDetails(source: CellularSource): Map<String, String> {
    val details = observationDetails(source.observation).toMutableMap()

    details["Signal"] = source.rssi?.let { "$it dBm" } ?: "Unavailable"
    details["Technology"] = source.technology.displayName
    source.cellId?.let { details["Cell identity"] = it.toString() }
    source.subscriptionId?.let { details["Subscription"] = it.toString() }
    source.band?.let { details["Band"] = it.toString() }

    if (source.isRegistered) {
        details["Status"] = "Serving Cell"
    }

    // Add detailed LTE/5G info if available
    source.details?.let { d ->
        // Cell identity
        d.formattedPlmn?.let { details["PLMN"] = it }
        d.mcc?.let { details["MCC"] = it.toString() }
        d.mnc?.let { details["MNC"] = it.toString() }
        d.tac?.let { details["TAC"] = it.toString() }
        d.eci?.let { details["ECI"] = it.toString() }
        d.calculatedEnb?.let { details["eNB"] = it.toString() }
        d.localCellId?.let { details["CID"] = it.toString() }
        d.pci?.let { details["PCI"] = it.toString() }

        d.nci?.let { details["NCI (36 bit)"] = it.toString() }
        d.nrArfcn?.let { details["NR-ARFCN"] = it.toString() }
        if (d.bands.isNotEmpty()) details["Reported bands"] = d.bands.joinToString()
        d.lac?.let { details["LAC"] = it.toString() }
        d.arfcn?.let { details["ARFCN"] = it.toString() }
        d.uarfcn?.let { details["UARFCN"] = it.toString() }
        if (source.technology == CellularTechnology.NR_5G) {
            details["SS-RSRP"] = d.ssRsrp?.let { "$it dBm" } ?: "Unavailable"
            details["SS-RSRQ"] = d.ssRsrq?.let { "$it dB" } ?: "Unavailable"
            details["SS-SINR"] = d.ssSinr?.let { "$it dB" } ?: "Unavailable"
            details["CSI-RSRP"] = d.csiRsrp?.let { "$it dBm" } ?: "Unavailable"
            details["CSI-RSRQ"] = d.csiRsrq?.let { "$it dB" } ?: "Unavailable"
            details["CSI-SINR"] = d.csiSinr?.let { "$it dB" } ?: "Unavailable"
        }

        // Signal quality
        d.rsrp?.let { details["RSRP"] = "$it dBm" }
        d.rsrq?.let { details["RSRQ"] = "$it dB" }
        d.rssnrDb?.let { details["RSSNR"] = "$it dB" }
        d.rssnr?.let { details["RSSNR (raw Android value)"] = it.toString() }
        d.cqi?.let { details["CQI"] = it.toString() }
        d.ta?.let { details["Timing Adv"] = it.toString() }

        // Network
        d.earfcn?.let { details["EARFCN"] = it.toString() }
        d.bandwidth?.let { details["Bandwidth"] = "$it MHz" }
    }

    return details
}

/**
 * Contribution bar showing source type percentage with colored bar.
 */
@Composable
private fun ContributionBar(
    icon: ImageVector,
    label: String,
    color: Color,
    percentage: Int,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(58.dp)
        )
        // Percentage bar
        Box(
            modifier = Modifier
                .weight(1f)
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
        // Percentage and count
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
