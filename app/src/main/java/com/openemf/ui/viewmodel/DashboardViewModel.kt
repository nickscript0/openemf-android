package com.openemf.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openemf.data.database.MeasurementEntity
import com.openemf.data.database.PlaceIcon
import com.openemf.data.repository.MeasurementRepository
import com.openemf.data.repository.PlaceWithStats
import com.openemf.sensors.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sensorModule: EMFSensorModule,
    private val repository: MeasurementRepository
) : ViewModel() {

    /** Current sensor state */
    val sensorState: StateFlow<SensorState> = sensorModule.sensorState

    /** Current scan budget */
    val scanBudget: StateFlow<ScanBudget> = sensorModule.scanBudget

    /** Latest measurement */
    private val _lastMeasurement = MutableStateFlow<Measurement?>(null)
    val lastMeasurement: StateFlow<Measurement?> = _lastMeasurement.asStateFlow()

    /** Measurement history (in-memory for now) */
    private val _measurementHistory = MutableStateFlow<List<Measurement>>(emptyList())
    val measurementHistory: StateFlow<List<Measurement>> = _measurementHistory.asStateFlow()

    /** Stored measurements from database (for map) */
    val storedMeasurements: StateFlow<List<MeasurementEntity>> = repository.getAllMeasurements()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Places with statistics */
    val places: StateFlow<List<PlaceWithStats>> = repository.getPlacesWithStats()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Permission status */
    val permissionStatus: StateFlow<PermissionStatus> = sensorModule.permissionStatus

    init {
        // Collect measurements from the sensor module
        viewModelScope.launch {
            sensorModule.measurements.collect { measurement ->
                _lastMeasurement.value = measurement
                _measurementHistory.update { history ->
                    listOf(measurement) + history.take(99) // Keep last 100
                }
                // Also save to database
                repository.saveMeasurement(measurement)
            }
        }
    }

    /**
     * Trigger a measurement scan.
     */
    fun measureNow() {
        viewModelScope.launch {
            sensorModule.measureNow()
        }
    }

    /**
     * Refresh permission status.
     */
    fun refreshPermissions() {
        sensorModule.checkPermissions()
    }

    /**
     * Update measurement context.
     */
    fun updateContext(context: MeasurementContext) {
        sensorModule.updateContext(context)
    }

    /**
     * Delete a measurement from history.
     */
    fun deleteMeasurement(measurement: Measurement) {
        _measurementHistory.update { history ->
            history.filter { it.id != measurement.id }
        }
    }

    /**
     * Clear all measurement history.
     */
    fun clearHistory() {
        _measurementHistory.value = emptyList()
    }

    /**
     * Add a new favorite place at the given location.
     */
    fun addPlace(latitude: Double, longitude: Double, name: String = "New Place", icon: PlaceIcon = PlaceIcon.OTHER) {
        viewModelScope.launch {
            repository.addPlace(name, icon, latitude, longitude)
        }
    }
}
