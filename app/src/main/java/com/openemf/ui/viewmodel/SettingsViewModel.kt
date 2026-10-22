package com.openemf.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openemf.data.export.RawCsvExporter
import com.openemf.data.preferences.PreferencesRepository
import com.openemf.data.repository.MeasurementRepository
import com.openemf.sensors.api.*
import com.openemf.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.openemf.recording.RecordingStateStore

/**
 * ViewModel for the Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sensorModule: EMFSensorModule,
    private val preferencesRepository: PreferencesRepository,
    private val measurementRepository: MeasurementRepository,
    private val rawCsvExporter: RawCsvExporter,
    private val recordingState: RecordingStateStore
) : ViewModel() {

    /** State for clear history operation */
    private val _clearHistoryState = MutableStateFlow<OperationState>(OperationState.Idle)
    val clearHistoryState: StateFlow<OperationState> = _clearHistoryState.asStateFlow()

    /** State for export operation */
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** Permission status */
    val permissionStatus: StateFlow<PermissionStatus> = sensorModule.permissionStatus

    /** Theme mode from preferences */
    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    /** Actual live-refresh state; a persisted toggle must not imply an active recorder. */
    val monitoringEnabled: StateFlow<Boolean> = sensorModule.monitoringActive

    /** Monitoring interval in milliseconds */
    val monitoringInterval: StateFlow<Long> = preferencesRepository.monitoringInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60_000L)

    /** Data contribution enabled */
    val dataContributionEnabled: StateFlow<Boolean> = preferencesRepository.dataContributionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Refresh permission status after returning from system settings.
     */
    fun refreshPermissions() {
        sensorModule.checkPermissions()
    }

    /**
     * Set the theme mode.
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    /**
     * Toggle background monitoring.
     */
    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMonitoringEnabled(enabled)
        }

        if (enabled) {
            val config = MonitoringConfig(
                intervalMs = monitoringInterval.value,
                modalities = setOf(Modality.WIFI, Modality.BLUETOOTH, Modality.CELLULAR),
                adaptToMotion = true,
                respectThrottle = true
            )
            sensorModule.startMonitoring(config)
        } else {
            sensorModule.stopMonitoring()
        }
    }

    /**
     * Update monitoring interval.
     */
    fun setMonitoringInterval(intervalMs: Long) {
        viewModelScope.launch {
            preferencesRepository.setMonitoringInterval(intervalMs)
        }

        // Restart monitoring if enabled
        if (monitoringEnabled.value) {
            setMonitoringEnabled(true)
        }
    }

    /**
     * Toggle data contribution.
     */
    fun setDataContributionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDataContributionEnabled(enabled)
        }
    }

    /**
     * Clear all measurement history.
     */
    fun clearHistory() {
        viewModelScope.launch {
            if (recordingState.state.value.active) {
                _clearHistoryState.value = OperationState.Error("Stop recording before clearing history")
                return@launch
            }
            _clearHistoryState.value = OperationState.Loading
            try {
                measurementRepository.deleteAllMeasurements()
                _clearHistoryState.value = OperationState.Success
            } catch (e: Exception) {
                _clearHistoryState.value = OperationState.Error(e.message ?: "Failed to clear history")
            }
        }
    }

    /**
     * Reset clear history state.
     */
    fun resetClearHistoryState() {
        _clearHistoryState.value = OperationState.Idle
    }

    /** The destination comes from Android's CreateDocument picker. */
    fun exportToCSV(destination: Uri) {
        if (_exportState.value is ExportState.Loading) return
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                _exportState.value = ExportState.Success(rawCsvExporter.export(destination))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Export failed; the selected file may be incomplete. ${e.message ?: ""}")
            }
        }
    }

    /**
     * Reset export state.
     */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }


}

/**
 * Generic operation state.
 */
sealed class OperationState {
    data object Idle : OperationState()
    data object Loading : OperationState()
    data object Success : OperationState()
    data class Error(val message: String) : OperationState()
}

/**
 * Export operation state with file path.
 */
sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val count: Long) : ExportState()
    data class Error(val message: String) : ExportState()
}
