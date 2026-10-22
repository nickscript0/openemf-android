package com.openemf.ui.navigation

import androidx.compose.runtime.*
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

            measurement?.let {
                MeasurementDetailScreen(
                    measurement = it,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // Settings
        composable(Screen.Settings.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
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
                onExportCSV = { settingsViewModel.exportToCSV(context) },
                onResetExportState = { settingsViewModel.resetExportState() },
                onOpenWebsite = { onOpenUrl("https://openemf.invisiblerainbows.com") },
                onOpenPrivacyPolicy = { onOpenUrl("https://openemf.invisiblerainbows.com/privacy") },
                onOpenGitHub = { onOpenUrl("https://github.com/apmai/openemf-android") }
            )
        }
    }
}
