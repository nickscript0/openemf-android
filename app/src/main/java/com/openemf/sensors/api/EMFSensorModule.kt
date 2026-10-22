package com.openemf.sensors.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Main interface for the EMF Sensor Module.
 * This is the public API that the UI layer interacts with.
 */
interface EMFSensorModule {
    
    /** Current sensor state (idle, scanning, throttled, etc.) */
    val sensorState: StateFlow<SensorState>
    
    /** Current scan budget status */
    val scanBudget: StateFlow<ScanBudget>
    val permissionStatus: StateFlow<PermissionStatus>
    val monitoringActive: StateFlow<Boolean>
    
    /** Stream of measurements (for continuous monitoring) */
    val measurements: Flow<Measurement>
    
    /** Perform a single measurement (foreground, user-triggered) */
    suspend fun measureNow(): Result<Measurement>

    /** Only an active, promoted recording service may use this path. */
    suspend fun record(sessionId: String, modalities: Set<Modality>): Result<Measurement>
    
    /** Start continuous monitoring */
    fun startMonitoring(config: MonitoringConfig)
    
    /** Stop continuous monitoring */
    fun stopMonitoring()
    
    /** Check required permissions */
    fun checkPermissions(): PermissionStatus
    
    /** Update context (motion state, foreground/background, etc.) */
    fun updateContext(context: MeasurementContext)
    fun setForeground(isForeground: Boolean)
}
