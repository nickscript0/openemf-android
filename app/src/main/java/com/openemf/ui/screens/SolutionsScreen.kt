package com.openemf.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openemf.sensors.api.*
import com.openemf.ui.theme.BluetoothColor
import com.openemf.ui.theme.CellularColor
import com.openemf.ui.theme.WiFiColor
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext

/**
 * Solutions screen providing actionable advice to reduce EMF exposure.
 * Shows contextual recommendations based on current measurements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolutionsScreen(
    currentMeasurement: Measurement?,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solutions") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current status card
            CurrentStatusCard(measurement = currentMeasurement)

            // Contextual advice based on measurement
            if (currentMeasurement != null) {
                ContextualAdviceCard(measurement = currentMeasurement)
                DominantSourceCard(measurement = currentMeasurement)
            }

            // Source-specific tips
            if (currentMeasurement != null) {
                SourceSpecificTipsCard(measurement = currentMeasurement)
            }

            // General tips
            GeneralTipsCard()

            // Distance guide
            DistanceGuideCard()

            // Quick actions
            QuickActionsCard()

            // Glossary
            GlossaryCard()

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CurrentStatusCard(measurement: Measurement?) {
    val score = measurement?.eScore?.score ?: 0
    val level = measurement?.eScore?.level ?: ExposureLevel.LOW

    val (statusColor, statusIcon, statusMessage) = when (level) {
        ExposureLevel.LOW -> Triple(
            Color(0xFF22C55E),
            Icons.Default.CheckCircle,
            "Your exposure is low. This level is reasonable and suits most people."
        )
        ExposureLevel.MODERATE -> Triple(
            Color(0xFFF59E0B),
            Icons.Default.Info,
            "Your exposure is moderate. Consider reducing time near strongest sources."
        )
        ExposureLevel.HIGH -> Triple(
            Color(0xFFEF4444),
            Icons.Default.Warning,
            "Your exposure is elevated. Review the recommendations below to reduce it."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (measurement != null) "E-Score: $score" else "No Measurement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ContextualAdviceCard(measurement: Measurement) {
    val adviceList = mutableListOf<Pair<ImageVector, String>>()

    // WiFi advice
    val strongWifi = measurement.wifiSources.filter { it.rssi?.let { value -> value > -50 } == true }
    if (strongWifi.isNotEmpty()) {
        adviceList.add(
            Icons.Default.Wifi to "Strong WiFi signal detected. Moving 2m away from your router could reduce WiFi exposure by 75%."
        )
    }

    // Bluetooth advice
    val connectedBt = measurement.bluetoothSources.filter { it.isConnected }
    if (connectedBt.isNotEmpty()) {
        adviceList.add(
            Icons.Default.Bluetooth to "Active Bluetooth connections detected. Using wired headphones eliminates head-proximity RF exposure."
        )
    }

    // Cellular advice
    val weakCellular = measurement.cellularSources.filter { it.rssi?.let { value -> value < -100 } == true }
    if (weakCellular.isNotEmpty()) {
        adviceList.add(
            Icons.Default.CellTower to "Weak cellular signal increases phone transmit power. Moving to better coverage reduces exposure."
        )
    }

    // High score advice
    if (measurement.eScore.score > 70) {
        adviceList.add(
            Icons.Default.MoveDown to "High exposure detected. Consider moving away from nearby devices or turning off unused radios."
        )
    }

    if (adviceList.isEmpty()) {
        adviceList.add(
            Icons.Default.Lightbulb to "Your current environment has reasonable EMF levels. No immediate action needed."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Personalized Recommendations",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            adviceList.forEach { (icon, advice) ->
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = advice,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun DominantSourceCard(measurement: Measurement) {
    val dominant = measurement.eScore.dominantSource ?: return
    val contribution = dominant.contributionPercent

    if (contribution < 30) return // Not really dominant

    val (color, icon) = when (dominant.type) {
        SourceType.WIFI -> WiFiColor to Icons.Default.Wifi
        SourceType.BLUETOOTH -> BluetoothColor to Icons.Default.Bluetooth
        SourceType.CELLULAR -> CellularColor to Icons.Default.CellTower
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "Dominant Source: ${dominant.name ?: "Unknown"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Contributes $contribution% of your total exposure",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Switching off this source would reduce your exposure by $contribution%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SourceSpecificTipsCard(measurement: Measurement) {
    var expandedSection by remember { mutableStateOf<SourceType?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Source-Specific Guide",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // WiFi section
            if (measurement.wifiSources.isNotEmpty()) {
                SourceTipsSection(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi (${measurement.wifiSources.size})",
                    color = WiFiColor,
                    expanded = expandedSection == SourceType.WIFI,
                    onToggle = {
                        expandedSection = if (expandedSection == SourceType.WIFI) null else SourceType.WIFI
                    },
                    tips = listOf(
                        "Keep router 2+ meters from work/sleep areas",
                        "Use ethernet for stationary devices",
                        "Turn off router at night (saves energy too)",
                        "5GHz WiFi has shorter range = less exposure at distance",
                        "Disable WiFi on devices when not needed"
                    )
                )
            }

            // Bluetooth section
            if (measurement.bluetoothSources.isNotEmpty()) {
                SourceTipsSection(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth (${measurement.bluetoothSources.size})",
                    color = BluetoothColor,
                    expanded = expandedSection == SourceType.BLUETOOTH,
                    onToggle = {
                        expandedSection = if (expandedSection == SourceType.BLUETOOTH) null else SourceType.BLUETOOTH
                    },
                    tips = listOf(
                        "Use wired headphones instead of Bluetooth",
                        "Keep Bluetooth speakers at arm's length",
                        "Disable Bluetooth when not actively using",
                        "BLE (Low Energy) devices emit less than classic BT",
                        "Remove unused paired devices"
                    )
                )
            }

            // Cellular section
            if (measurement.cellularSources.isNotEmpty()) {
                SourceTipsSection(
                    icon = Icons.Default.CellTower,
                    title = "Cellular (${measurement.cellularSources.size})",
                    color = CellularColor,
                    expanded = expandedSection == SourceType.CELLULAR,
                    onToggle = {
                        expandedSection = if (expandedSection == SourceType.CELLULAR) null else SourceType.CELLULAR
                    },
                    tips = listOf(
                        "Use speakerphone or wired headset for calls",
                        "Text instead of call when possible",
                        "Wait for good signal before calling",
                        "Don't keep phone in pocket - use bag",
                        "Enable airplane mode when sleeping",
                        "Weak signal = phone transmits more power"
                    )
                )
            }

            // Magnetic field section
            measurement.magneticField?.let {
                SourceTipsSection(
                    icon = Icons.Default.ElectricalServices,
                    title = "Magnetic Fields (ELF-EMF)",
                    color = Color(0xFF10B981),
                    expanded = expandedSection == null && measurement.magneticField != null,
                    onToggle = {},
                    tips = listOf(
                        "Keep distance from transformers and motors",
                        "Move electrical devices away from bed",
                        "Unplug chargers when not in use",
                        "Avoid sleeping near smart meters",
                        "Use battery-powered clocks near bed"
                    )
                )
            }
        }
    }
}

@Composable
private fun SourceTipsSection(
    icon: ImageVector,
    title: String,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    tips: List<String>
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tips.forEach { tip ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = 36.dp))
    }
}

@Composable
private fun GeneralTipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "General Tips",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            TipRow(
                icon = Icons.Default.PhoneAndroid,
                title = "Keep Distance",
                description = "Don't sleep with your phone under your pillow. Keep it at least 1m away."
            )

            TipRow(
                icon = Icons.Default.WifiOff,
                title = "Night Mode",
                description = "Turn off WiFi router at night to reduce 8 hours of exposure."
            )

            TipRow(
                icon = Icons.Default.Headphones,
                title = "Wired Audio",
                description = "Use wired headphones for calls to keep phone away from head."
            )

            TipRow(
                icon = Icons.Default.SignalCellular4Bar,
                title = "Strong Signal",
                description = "Make calls where cellular signal is strong - phone transmits less."
            )
        }
    }
}

@Composable
private fun TipRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DistanceGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SocialDistance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Distance is Your Friend",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "RF power follows the inverse square law:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            DistanceRow("1x", "100%", "Baseline")
            DistanceRow("2x", "25%", "↓ 75%")
            DistanceRow("3x", "11%", "↓ 89%")
            DistanceRow("4x", "6%", "↓ 94%")

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Doubling your distance from a source reduces exposure by 4x!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DistanceRow(distance: String, power: String, reduction: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = distance,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = power,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = reduction,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF22C55E),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun QuickActionsCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.AirplanemodeActive,
                    label = "Airplane\nMode",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                    }
                )
                QuickActionButton(
                    icon = Icons.Default.WifiOff,
                    label = "WiFi\nSettings",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                )
                QuickActionButton(
                    icon = Icons.Default.BluetoothDisabled,
                    label = "Bluetooth\nSettings",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to open system settings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GlossaryCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Glossary - Understanding EMF Terms",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // General terms
                GlossarySection("General") {
                    GlossaryTerm(
                        term = "E-Score",
                        definition = "Exposure Score (0-100) representing your combined EMF exposure. Lower is better: 0-10 Excellent, 11-25 Good, 26-50 Moderate, 51-75 Elevated, 76-90 High, 90+ Very High."
                    )
                    GlossaryTerm(
                        term = "dBm",
                        definition = "Decibels relative to milliwatt. Measures signal strength. Closer to 0 = stronger. -50 dBm is strong, -100 dBm is weak."
                    )
                    GlossaryTerm(
                        term = "EMF",
                        definition = "Electromagnetic Field. Energy waves emitted by electronic devices and wireless communications."
                    )
                    GlossaryTerm(
                        term = "RF",
                        definition = "Radio Frequency. The type of EMF used for wireless communication (WiFi, cellular, Bluetooth)."
                    )
                }

                // WiFi terms
                GlossarySection("Wi-Fi") {
                    GlossaryTerm(
                        term = "SSID",
                        definition = "Service Set Identifier. The network name you see when connecting to WiFi."
                    )
                    GlossaryTerm(
                        term = "BSSID",
                        definition = "Basic Service Set Identifier. The router's unique hardware address (MAC address)."
                    )
                    GlossaryTerm(
                        term = "2.4 GHz / 5 GHz / 6 GHz",
                        definition = "WiFi frequency bands. 2.4 GHz travels farther but is slower. 5/6 GHz are faster but shorter range."
                    )
                    GlossaryTerm(
                        term = "Channel Width",
                        definition = "Bandwidth used (20/40/80/160 MHz). Wider = faster but more spectrum used."
                    )
                    GlossaryTerm(
                        term = "WiFi 4/5/6/6E/7",
                        definition = "WiFi generations (802.11n/ac/ax). Newer versions are faster and more efficient."
                    )
                }

                // Cellular terms
                GlossarySection("Cellular") {
                    GlossaryTerm(
                        term = "RSRP",
                        definition = "Reference Signal Received Power. LTE signal strength in dBm. Good: >-90, Poor: <-110."
                    )
                    GlossaryTerm(
                        term = "RSRQ",
                        definition = "Reference Signal Received Quality. Signal quality in dB. Good: >-10, Poor: <-15."
                    )
                    GlossaryTerm(
                        term = "PCI",
                        definition = "Physical Cell ID. Identifier for the cell tower sector you're connected to."
                    )
                    GlossaryTerm(
                        term = "MCC/MNC",
                        definition = "Mobile Country/Network Code. Identifies the carrier (e.g., 310/260 = T-Mobile US)."
                    )
                    GlossaryTerm(
                        term = "LTE/4G/5G",
                        definition = "Cellular generations. 5G is fastest but uses more power when signal is weak."
                    )
                }

                // Bluetooth terms
                GlossarySection("Bluetooth") {
                    GlossaryTerm(
                        term = "BLE",
                        definition = "Bluetooth Low Energy. Uses less power and emits less RF than classic Bluetooth."
                    )
                    GlossaryTerm(
                        term = "Classic BT",
                        definition = "Traditional Bluetooth for audio streaming. Higher power than BLE."
                    )
                }
            }
        }
    }
}

@Composable
private fun GlossarySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    Column(content = content)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun GlossaryTerm(
    term: String,
    definition: String
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = term,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = definition,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
