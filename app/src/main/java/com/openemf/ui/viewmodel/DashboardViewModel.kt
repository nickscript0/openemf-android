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

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError = _historyError.asStateFlow()

    /** Durable summaries are the only input to history and statistics. */
    val measurementHistory: StateFlow<List<MeasurementEntity>> = repository.getAllMeasurements()
        .catch { _historyError.value = it.message ?: "Could not read capture history"; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources = repository.getSources()
        .catch { _historyError.value = it.message ?: "Could not read source history"; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observations(id: String) = repository.getObservations(id)
        .catch { _historyError.value = it.message ?: "Could not read observations"; emit(emptyList()) }
    suspend fun acquisitions(id: String) = repository.getAcquisitions(id)
    suspend fun sourceHistory(type: SourceType, sourceId: String, before: Long?) =
        repository.getSourceHistory(type, sourceId, beforeRowId = before)

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

    fun deleteMeasurement(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteMeasurement(id)
                _historyError.value = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _historyError.value = e.message ?: "Could not delete capture"
            }
        }
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
