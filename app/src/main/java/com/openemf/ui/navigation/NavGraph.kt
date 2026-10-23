package com.openemf.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.openemf.data.database.RadioAcquisitionEntity
import com.openemf.sensors.api.SourceType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openemf.ui.screens.*
import com.openemf.ui.theme.ThemeMode
import com.openemf.ui.viewmodel.DashboardViewModel
import com.openemf.ui.viewmodel.SettingsViewModel
import com.openemf.ui.viewmodel.RecordingViewModel
import com.openemf.ui.viewmodel.SourceHistoryViewModel
import com.openemf.ui.components.RecordingControls

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object History : Screen("history")
    data object SourceHistory : Screen("source/{type}/{sourceId}") {
        fun createRoute(type: SourceType, sourceId: String) = "source/${type.name}/${Uri.encode(sourceId)}"
    }
    data object MeasurementDetail : Screen("measurement/{measurementId}") {
        fun createRoute(measurementId: String) = "measurement/$measurementId"
    }
}

/**
 * Main navigation graph.
 * Technical signals, relative index, and durable trends share the main scaffold.
 */
@Composable
fun OpenEMFNavGraph(
    navController: NavHostController = rememberNavController(),
    onRequestPermissions: () -> Unit,
    onStartRecording: (Long) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val recordingViewModel: RecordingViewModel = hiltViewModel()
    val recordingState by recordingViewModel.state.collectAsStateWithLifecycle()
    val sessions by recordingViewModel.recentSessions.collectAsStateWithLifecycle()
    val journalError by recordingViewModel.journalError.collectAsStateWithLifecycle()
    val recordingControls: @Composable () -> Unit = {
        RecordingControls(recordingState, sessions.firstOrNull(), journalError, onStartRecording,
            recordingViewModel::pause, recordingViewModel::resume, recordingViewModel::stop)
    }

    // Collect states
    val sensorState by dashboardViewModel.sensorState.collectAsStateWithLifecycle()
    val scanBudget by dashboardViewModel.scanBudget.collectAsStateWithLifecycle()
    val lastMeasurement by dashboardViewModel.lastMeasurement.collectAsStateWithLifecycle()
    val measurementHistory by dashboardViewModel.measurementHistory.collectAsStateWithLifecycle()
    val sources by dashboardViewModel.sources.collectAsStateWithLifecycle()
    val historyError by dashboardViewModel.historyError.collectAsStateWithLifecycle()

    val settingsPermissions by settingsViewModel.permissionStatus.collectAsStateWithLifecycle()
    val monitoringEnabled by settingsViewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val monitoringInterval by settingsViewModel.monitoringInterval.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val clearHistoryState by settingsViewModel.clearHistoryState.collectAsStateWithLifecycle()
    val exportState by settingsViewModel.exportState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        // Technical signals are the default destination.
        composable(Screen.Main.route) {
            MainAppScaffold(
                sensorState = sensorState,
                scanBudget = scanBudget,
                lastMeasurement = lastMeasurement,
                measurementHistory = measurementHistory,
                historyError = historyError,
                recordingState = recordingState,
                recordingContent = recordingControls,
                onStopRecording = recordingViewModel::stop,
                onRequestPermissions = onRequestPermissions,
                onSourceClick = { type, id -> navController.navigate(Screen.SourceHistory.createRoute(type, id)) },
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onScanClick = { if (!recordingState.active) dashboardViewModel.measureNow() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        // Measurement Detail
        composable(
            route = Screen.MeasurementDetail.route,
            arguments = listOf(
                navArgument("measurementId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val measurementId = backStackEntry.arguments?.getString("measurementId")
            val measurement = measurementHistory.find { it.id == measurementId }

            val observations by remember(measurementId) { dashboardViewModel.observations(measurementId ?: "") }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            var detailError by remember(measurementId) { mutableStateOf<String?>(null) }
            val acquisitions by produceState<List<RadioAcquisitionEntity>>(emptyList(), measurementId) {
                try { value = dashboardViewModel.acquisitions(measurementId ?: "") }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { detailError = e.message ?: "Could not read acquisition reports" }
            }
            MeasurementDetailScreen(
                measurement = measurement, observations = observations, acquisitions = acquisitions,
                error = detailError ?: historyError,
                onBackClick = { navController.popBackStack() },
                onSourceClick = { navController.navigate(Screen.SourceHistory.createRoute(it.sourceType, it.sourceId)) }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(measurementHistory, sources, historyError,
                onBackClick = { navController.popBackStack() },
                onDeleteClick = { dashboardViewModel.deleteMeasurement(it.id) },
                onMeasurementClick = { navController.navigate(Screen.MeasurementDetail.createRoute(it.id)) },
                onSourceClick = { navController.navigate(Screen.SourceHistory.createRoute(it.sourceType, it.sourceId)) })
        }

        composable(Screen.SourceHistory.route, arguments = listOf(
            navArgument("type") { type = NavType.StringType }, navArgument("sourceId") { type = NavType.StringType }
        )) { entry ->
            val model: SourceHistoryViewModel = hiltViewModel(entry)
            val timeline by model.timeline.collectAsStateWithLifecycle()
            val chart by model.chart.collectAsStateWithLifecycle()
            SourceHistoryScreen(model.sourceId, timeline.records, timeline.loading, timeline.error, timeline.hasMore,
                chart = chart, onRange = model::selectRange,
                onLoadMore = model::loadMore,
                onRefresh = model::refresh,
                onBackClick = { navController.popBackStack() })
        }

        // Settings
        composable(Screen.Settings.route) {
            val exportContract = remember { object : ActivityResultContracts.CreateDocument("text/csv") {
                override fun createIntent(context: android.content.Context, input: String) =
                    super.createIntent(context, input).putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true)
            } }
            val exportDocument = rememberLauncherForActivityResult(exportContract) { uri ->
                uri?.let(settingsViewModel::exportToCSV)
            }
            SettingsScreen(
                permissionStatus = settingsPermissions,
                monitoringEnabled = monitoringEnabled,
                monitoringInterval = monitoringInterval,
                themeMode = themeMode,
                clearHistoryState = clearHistoryState,
                exportState = exportState,
                recordingContent = recordingControls,
                recordingActive = recordingState.active,
                onBackClick = { navController.popBackStack() },
                onRequestPermissions = onRequestPermissions,
                onMonitoringToggle = { settingsViewModel.setMonitoringEnabled(it) },
                onIntervalChange = { settingsViewModel.setMonitoringInterval(it) },
                onThemeModeChange = { settingsViewModel.setThemeMode(it) },
                onClearHistory = { settingsViewModel.clearHistory() },
                onResetClearHistoryState = { settingsViewModel.resetClearHistoryState() },
                onExportCSV = { if (exportState !is com.openemf.ui.viewmodel.ExportState.Loading)
                    exportDocument.launch("openemf_signals_${System.currentTimeMillis()}.csv") },
                onResetExportState = { settingsViewModel.resetExportState() },
                onOpenWebsite = { onOpenUrl("https://openemf.invisiblerainbows.com") },
                onOpenPrivacyPolicy = { onOpenUrl("https://openemf.invisiblerainbows.com/privacy") },
                onOpenGitHub = { onOpenUrl("https://github.com/apmai/openemf-android") }
            )
        }
    }
}
