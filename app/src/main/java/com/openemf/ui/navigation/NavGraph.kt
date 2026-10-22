package com.openemf.ui.navigation

import androidx.compose.runtime.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.openemf.data.database.ObservationRecord
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
 * Uses MainAppScaffold with bottom navigation for Live/Web/Statistics/Solutions.
 */
@Composable
fun OpenEMFNavGraph(
    navController: NavHostController = rememberNavController(),
    onRequestPermissions: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    // Collect states
    val sensorState by dashboardViewModel.sensorState.collectAsState()
    val scanBudget by dashboardViewModel.scanBudget.collectAsState()
    val lastMeasurement by dashboardViewModel.lastMeasurement.collectAsState()
    val measurementHistory by dashboardViewModel.measurementHistory.collectAsState()
    val sources by dashboardViewModel.sources.collectAsState()
    val historyError by dashboardViewModel.historyError.collectAsState()

    val settingsPermissions by settingsViewModel.permissionStatus.collectAsState()
    val monitoringEnabled by settingsViewModel.monitoringEnabled.collectAsState()
    val monitoringInterval by settingsViewModel.monitoringInterval.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val clearHistoryState by settingsViewModel.clearHistoryState.collectAsState()
    val exportState by settingsViewModel.exportState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        // Main screen with bottom navigation (Live, Web, Statistics, Solutions)
        composable(Screen.Main.route) {
            MainAppScaffold(
                sensorState = sensorState,
                scanBudget = scanBudget,
                lastMeasurement = lastMeasurement,
                measurementHistory = measurementHistory,
                historyError = historyError,
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onScanClick = { dashboardViewModel.measureNow() },
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
                .collectAsState(initial = emptyList())
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
            val type = SourceType.valueOf(entry.arguments!!.getString("type")!!)
            val sourceId = entry.arguments!!.getString("sourceId")!!
            var records by remember(type, sourceId) { mutableStateOf<List<ObservationRecord>>(emptyList()) }
            var request by remember { mutableIntStateOf(0) }
            var loading by remember { mutableStateOf(true) }
            var hasMore by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(type, sourceId, request) {
                loading = true
                error = null
                try {
                    val page = dashboardViewModel.sourceHistory(type, sourceId, records.lastOrNull()?.observation?.rowId)
                    records = records + page
                    hasMore = page.size == 100
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Exception) { error = e.message ?: "Could not load signal history"
                } finally { loading = false }
            }
            SourceHistoryScreen(sourceId, records, loading, error, hasMore,
                onLoadMore = { request++ },
                onRefresh = { records = emptyList(); hasMore = true; request++ },
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
