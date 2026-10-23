package com.openemf.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import android.os.Build
import com.openemf.app.BuildConfig
import com.openemf.sensors.api.Permission
import com.openemf.sensors.api.PermissionStatus
import com.openemf.ui.theme.ThemeMode
import com.openemf.ui.viewmodel.ExportState
import com.openemf.ui.viewmodel.OperationState

/**
 * Settings screen for app configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    permissionStatus: PermissionStatus,
    monitoringEnabled: Boolean,
    monitoringInterval: Long,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    clearHistoryState: OperationState = OperationState.Idle,
    exportState: ExportState = ExportState.Idle,
    recordingContent: @Composable () -> Unit = {},
    recordingActive: Boolean = false,
    onBackClick: () -> Unit,
    onRequestPermissions: () -> Unit,
    onMonitoringToggle: (Boolean) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onResetClearHistoryState: () -> Unit = {},
    onExportCSV: () -> Unit = {},
    onResetExportState: () -> Unit = {},
    onOpenWebsite: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenGitHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            Box(Modifier.padding(16.dp)) { recordingContent() }
            // Permissions section
            SettingsSection(title = "Permissions") {
                PermissionItem(
                    permission = Permission.FINE_LOCATION,
                    label = "Location Access",
                    description = "Required for WiFi and cellular scanning",
                    isGranted = Permission.FINE_LOCATION in permissionStatus.granted
                )

                // Bluetooth scan permission only required on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionItem(
                        permission = Permission.BLUETOOTH_SCAN,
                        label = "Bluetooth Scan",
                        description = "Required for Bluetooth device detection",
                        isGranted = Permission.BLUETOOTH_SCAN in permissionStatus.granted
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PermissionItem(
                        permission = Permission.BLUETOOTH_CONNECT,
                        label = "Bluetooth Device Information",
                        description = "Required for device names and paired inventory",
                        isGranted = Permission.BLUETOOTH_CONNECT in permissionStatus.granted
                    )
                }

                if (permissionStatus.denied.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Permissions")
                    }
                }
            }

            HorizontalDivider()

            // Appearance section
            SettingsSection(title = "Appearance") {
                ThemeModeSelector(
                    currentMode = themeMode,
                    onModeChange = onThemeModeChange
                )
            }

            HorizontalDivider()

            // Monitoring section
            SettingsSection(title = "Live Refresh") {
                SettingsToggle(
                    icon = Icons.Default.Notifications,
                    title = "Refresh While App Is Open",
                    subtitle = "Refresh measurements while this app is visible",
                    checked = monitoringEnabled,
                    enabled = !recordingActive,
                    onCheckedChange = onMonitoringToggle
                )

                if (monitoringEnabled) {
                    IntervalSelector(
                        currentInterval = monitoringInterval,
                        onIntervalChange = onIntervalChange
                    )
                }
            }

            HorizontalDivider()

            // About section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = {}
                )

                SettingsItem(
                    icon = Icons.Default.Language,
                    title = "Companion Website",
                    subtitle = "openemf.invisiblerainbows.com",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenWebsite
                )

                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "Source Code",
                    subtitle = "View on GitHub",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenGitHub
                )

                SettingsItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How we handle your data",
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = onOpenPrivacyPolicy
                )
            }

            HorizontalDivider()

            // Data section
            SettingsSection(title = "Data") {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export raw signal CSV",
                    subtitle = when (exportState) {
                        is ExportState.Loading -> "Exporting..."
                        is ExportState.Success -> "Exported ${exportState.count} signal observations"
                        is ExportState.Error -> exportState.message
                        else -> "Choose a file for all raw signal observations"
                    },
                    onClick = onExportCSV
                )

                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "Clear History",
                    subtitle = when (clearHistoryState) {
                        is OperationState.Loading -> "Clearing..."
                        is OperationState.Success -> "History cleared"
                        is OperationState.Error -> clearHistoryState.message
                        else -> if (recordingActive) "Stop recording before clearing history" else "Delete all stored measurements"
                    },
                    onClick = { showClearHistoryDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Clear History Confirmation Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Clear History?") },
            text = {
                Text("This will permanently delete all your measurement history. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Handle state changes
    LaunchedEffect(clearHistoryState) {
        if (clearHistoryState is OperationState.Success) {
            kotlinx.coroutines.delay(2000)
            onResetClearHistoryState()
        }
    }

    LaunchedEffect(exportState) {
        if (exportState is ExportState.Success || exportState is ExportState.Error) {
            kotlinx.coroutines.delay(3000)
            onResetExportState()
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = trailingIcon?.let {
            {
                Icon(
                    it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun PermissionItem(
    permission: Permission,
    label: String,
    description: String,
    isGranted: Boolean
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        },
        trailingContent = {
            Text(
                text = if (isGranted) "Granted" else "Required",
                style = MaterialTheme.typography.labelMedium,
                color = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    )
}

@Composable
private fun IntervalSelector(
    currentInterval: Long,
    onIntervalChange: (Long) -> Unit
) {
    val intervals = listOf(
        30_000L to "30 seconds",
        60_000L to "1 minute",
        120_000L to "2 minutes",
        300_000L to "5 minutes",
        600_000L to "10 minutes"
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Scan Interval",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            intervals.forEach { (interval, label) ->
                FilterChip(
                    selected = currentInterval == interval,
                    onClick = { onIntervalChange(interval) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/**
 * Theme mode selector with Sun (Light), Moon (Dark), and System icons.
 */
@Composable
private fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // Light mode
        FilterChip(
            selected = currentMode == ThemeMode.LIGHT,
            onClick = { onModeChange(ThemeMode.LIGHT) },
            label = { Text("Light") },
            leadingIcon = {
                Icon(
                    Icons.Default.LightMode,
                    contentDescription = "Light Mode",
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        // System mode
        FilterChip(
            selected = currentMode == ThemeMode.SYSTEM,
            onClick = { onModeChange(ThemeMode.SYSTEM) },
            label = { Text("System") },
            leadingIcon = {
                Icon(
                    Icons.Default.SettingsBrightness,
                    contentDescription = "System Mode",
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        // Dark mode
        FilterChip(
            selected = currentMode == ThemeMode.DARK,
            onClick = { onModeChange(ThemeMode.DARK) },
            label = { Text("Dark") },
            leadingIcon = {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = "Dark Mode",
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}
