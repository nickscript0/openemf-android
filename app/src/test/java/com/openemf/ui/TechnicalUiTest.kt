package com.openemf.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.openemf.data.fixtureMeasurement
import com.openemf.data.fixtureTime
import com.openemf.data.database.ObservationRecord
import com.openemf.data.repository.observationEntities
import com.openemf.recording.RecordingState
import com.openemf.sensors.api.*
import com.openemf.ui.components.SignalHistoryChart
import com.openemf.ui.model.HistoryRange
import com.openemf.ui.navigation.MainAppScaffold
import com.openemf.ui.screens.SignalsScreen
import com.openemf.ui.theme.OpenEMFTheme
import com.openemf.ui.theme.ThemeMode
import com.openemf.ui.viewmodel.SourceChartState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class, qualifiers = "w448dp-h956dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TechnicalUiTest {
    @get:Rule val compose = createComposeRule()
    @Before fun clock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(300)) }
    private fun freshMeasurement(): Measurement {
        val m = fixtureMeasurement()
        val now = SystemClock.elapsedRealtime()
        return m.copy(timestamp = Instant.now(), wifiSources = m.wifiSources.map {
            it.copy(observation = Observation.fromBootTime(now - 1000, now, Instant.now(), cached = true))
        })
    }
    private fun image(name: String) {
        val directory = File("build/reports/technical-ui").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun signalsShowTechnicalPowerThenSwitchRadiosWithoutInventingBluetoothPower() {
        var history: Pair<SourceType, String>? = null
        compose.setContent {
            OpenEMFTheme(themeMode = ThemeMode.LIGHT) {
                SignalsScreen(freshMeasurement(), SensorState.Idle, ScanBudget.initial(), true,
                    {}, {}, {}, {}, { type, id -> history = type to id }, {})
            }
        }
        compose.onNodeWithText("Home").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("-61 dBm").assertIsDisplayed()
        image("signals-light")
        compose.onNodeWithText("Wi-Fi", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithText("Bluetooth", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Speaker").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Technical details").performClick()
        compose.onNodeWithText("Unknown (discovery only)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Signal history").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SourceType.BLUETOOTH to "bt:bb", history) }
    }

    @Test fun technicalSignalsAreDefaultAndRelativeDashboardHasNoSafetyBands() {
        compose.setContent {
            OpenEMFTheme(themeMode = ThemeMode.LIGHT) {
                MainAppScaffold(SensorState.Idle, ScanBudget.initial(), freshMeasurement(), emptyList(), null,
                    RecordingState(), {}, {}, {}, {}, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithText("Radio measurements").assertIsDisplayed()
        compose.onNodeWithText("Relative index").performClick()
        compose.onNodeWithText("Received-signal overview").assertIsDisplayed()
        compose.onAllNodesWithText("Excellent").assertCountEquals(0)
        compose.onAllNodesWithText("Good").assertCountEquals(0)
        image("relative-index-light")
    }

    @Test fun nrChartSeparatesQualityUnitsAndCanInspectSamplesInDarkTheme() {
        val cell = fixtureMeasurement().observationEntities().single { it.sourceType == SourceType.CELLULAR }
            .copy(rowId = 1, freshnessAtCapture = Freshness.FRESH)
        val first = ObservationRecord(cell, fixtureTime, CaptureMode.RECORDING, "session", 200000,
            42, AcquisitionStatus.UPDATED, null, false, MotionState.UNKNOWN)
        val second = first.copy(observation = cell.copy(rowId = 2, observedAt = fixtureTime.minusSeconds(1),
            observedElapsedRealtimeMs = 199000, cellular = cell.cellular!!.copy(details = cell.cellular.details!!.copy(ssSinr = 0))))
        compose.setContent {
            OpenEMFTheme(themeMode = ThemeMode.DARK) {
                SignalHistoryChart(SourceChartState(range = HistoryRange.HOUR, records = listOf(second, first), since = fixtureTime.minusSeconds(3600),
                    until = fixtureTime, loading = false), {})
            }
        }
        compose.onNodeWithText("SS-SINR").performClick()
        compose.onNodeWithText("SS-SINR · dB").assertIsDisplayed()
        compose.onNodeWithText("Older").performClick()
        compose.onNodeWithText("17 dB ·", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Newer").performClick()
        compose.onNodeWithText("0 dB ·", substring = true).assertIsDisplayed()
        image("nr-quality-dark")
    }

    @Test fun recordingKeepsManualScanDisabledAndFilteringPreservesPermissionStatus() {
        var permissions = 0
        compose.setContent {
            OpenEMFTheme(themeMode = ThemeMode.LIGHT) {
                SignalsScreen(freshMeasurement(), SensorState.PermissionRequired(listOf(Permission.BLUETOOTH_SCAN), true),
                    ScanBudget.initial(), false, {}, { permissions++ }, {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Measure now").assertIsNotEnabled()
        compose.onNodeWithText("Permissions").performClick()
        compose.runOnIdle { assertEquals(1, permissions) }
        compose.onNodeWithText("Filter name or identity").performTextInput("does-not-exist")
        compose.onNodeWithText("No matching signals in this capture.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan permissions are missing.", substring = true).performScrollTo().assertIsDisplayed()
    }
}
