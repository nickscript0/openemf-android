package com.openemf.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.openemf.data.database.MeasurementEntity
import com.openemf.data.repository.PlaceWithStats
import com.openemf.sensors.api.*
import com.openemf.ui.screens.DashboardScreen
import com.openemf.ui.screens.StatisticsScreen
import com.openemf.ui.screens.SolutionsScreen
import com.openemf.recording.RecordingState

/**
 * Navigation destinations for the app.
 */
enum class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIVE(
        label = "Live",
        selectedIcon = Icons.Filled.Sensors,
        unselectedIcon = Icons.Outlined.Sensors
    ),
    WEB(
        label = "Web",
        selectedIcon = Icons.Filled.Language,
        unselectedIcon = Icons.Outlined.Language
    ),
    STATISTICS(
        label = "Statistics",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    ),
    SOLUTIONS(
        label = "Solutions",
        selectedIcon = Icons.Filled.Lightbulb,
        unselectedIcon = Icons.Outlined.Lightbulb
    )
}

/**
 * Main app scaffold with bottom navigation.
 * Provides navigation between Live, Web, Statistics, and Solutions screens.
 */
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
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(NavDestination.LIVE) }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            Column {
                if (recordingState.active) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recording: ${recordingState.phase.name.lowercase()}")
                        TextButton(onClick = onStopRecording) { Text("Stop") }
                    }
                }
                NavigationBar {
                    NavDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (currentDestination == destination) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        // Content based on current destination
        when (currentDestination) {
            NavDestination.LIVE -> {
                DashboardScreen(
                    sensorState = sensorState,
                    recordingContent = recordingContent,
                    scansEnabled = !recordingState.active,
                    scanBudget = scanBudget,
                    lastMeasurement = lastMeasurement,
                    onScanClick = onScanClick,
                    onHistoryClick = onHistoryClick,
                    onSolutionsClick = { currentDestination = NavDestination.SOLUTIONS },
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.WEB -> {
                // Open website in browser and return to Live
                LaunchedEffect(Unit) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://openemf.invisiblerainbows.com"))
                    context.startActivity(intent)
                    currentDestination = NavDestination.LIVE
                }
                // Show Live screen while browser opens
                DashboardScreen(
                    sensorState = sensorState,
                    recordingContent = recordingContent,
                    scansEnabled = !recordingState.active,
                    scanBudget = scanBudget,
                    lastMeasurement = lastMeasurement,
                    onScanClick = onScanClick,
                    onHistoryClick = onHistoryClick,
                    onSolutionsClick = { currentDestination = NavDestination.SOLUTIONS },
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.STATISTICS -> {
                StatisticsScreen(
                    measurementHistory = measurementHistory,
                    historyError = historyError,
                    onHistoryClick = onHistoryClick,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
            NavDestination.SOLUTIONS -> {
                SolutionsScreen(
                    currentMeasurement = lastMeasurement,
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
