package com.openemf.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openemf.data.database.MeasurementEntity
import com.openemf.data.preferences.PreferencesRepository
import com.openemf.data.repository.MeasurementRepository
import com.openemf.sensors.api.*
import com.openemf.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sensorModule: EMFSensorModule,
    private val preferencesRepository: PreferencesRepository,
    private val measurementRepository: MeasurementRepository
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

    /**
     * Export measurements to CSV file.
     */
    fun exportToCSV(context: Context) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val measurements = measurementRepository.getAllMeasurementsForExport()
                if (measurements.isEmpty()) {
                    _exportState.value = ExportState.Error("No measurements to export")
                    return@launch
                }

                val csvContent = buildCSV(measurements)
                val fileName = "openemf_export_${System.currentTimeMillis()}.csv"
                val file = File(context.getExternalFilesDir(null), fileName)
                file.writeText(csvContent)

                _exportState.value = ExportState.Success(file.absolutePath, measurements.size)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Failed to export")
            }
        }
    }

    /**
     * Reset export state.
     */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    private fun buildCSV(measurements: List<MeasurementEntity>): String {
        val sb = StringBuilder()
        // Header
        sb.appendLine("ID,Timestamp,E-Score,Exposure Level,WiFi Count,Bluetooth Count,Cellular Count,WiFi %,Bluetooth %,Cellular %,Latitude,Longitude,Accuracy,Place ID,Dominant Source,Dominant Type,Dominant %")

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        // Data rows
        measurements.forEach { m ->
            sb.appendLine(listOf(
                m.id,
                formatter.format(m.timestamp),
                m.eScore,
                m.exposureLevel,
                m.wifiCount,
                m.bluetoothCount,
                m.cellularCount,
                m.wifiPercent,
                m.bluetoothPercent,
                m.cellularPercent,
                m.latitude ?: "",
                m.longitude ?: "",
                m.accuracy ?: "",
                m.placeId ?: "",
                m.dominantSourceName ?: "",
                m.dominantSourceType ?: "",
                m.dominantSourcePercent ?: ""
            ).joinToString(","))
        }

        return sb.toString()
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
    data class Success(val filePath: String, val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}
