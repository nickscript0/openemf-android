package com.openemf.sensors.api

import java.time.Instant

/**
 * Represents the current state of the sensor module.
 * UI layer observes this to show appropriate feedback.
 */
sealed class SensorState {
    
    /** Ready to scan */
    data object Idle : SensorState()
    
    /** Currently performing a scan */
    data class Scanning(
        val startedAt: Instant,
        val modalities: Set<Modality>
    ) : SensorState()
    
    /** Scan complete with results */
    data class Complete(
        val measurement: Measurement,
        val confidence: ConfidenceFlags,
        val durationMs: Long
    ) : SensorState()
    
    /** Throttle limit reached, cannot scan */
    data class ThrottleLimited(
        val scansRemaining: Int,
        val resetInSeconds: Int,
        val cachedMeasurement: Measurement?
    ) : SensorState()
    
    /** Permissions required before scanning */
    data class PermissionRequired(
        val missing: List<Permission>,
        val canProceedDegraded: Boolean
    ) : SensorState()
    
    /** Error occurred */
    data class Error(
        val type: ErrorType,
        val message: String,
        val recoverable: Boolean
    ) : SensorState()
}

enum class Modality {
    WIFI,
    BLUETOOTH,
    CELLULAR
}

enum class Permission {
    FINE_LOCATION,
    COARSE_LOCATION,
    BLUETOOTH_SCAN,
    BLUETOOTH_CONNECT,
    NEARBY_WIFI_DEVICES
}

enum class ErrorType {
    WIFI_DISABLED,
    BLUETOOTH_DISABLED,
    LOCATION_DISABLED,
    AIRPLANE_MODE,
    SCAN_FAILED,
    UNKNOWN
}

/**
 * Confidence flags for each modality.
 * Low confidence when using cached data or degraded permissions.
 */
data class ConfidenceFlags(
    val wifi: Float,        // 0-1
    val bluetooth: Float,   // 0-1
    val cellular: Float,    // 0-1
    val overall: Float      // Weighted average
) {
    companion object {
        val FULL = ConfidenceFlags(1f, 1f, 1f, 1f)
        val NONE = ConfidenceFlags(0f, 0f, 0f, 0f)
    }
}

/**
 * Tracks WiFi scan budget to respect Android throttling limits.
 * Android 9+: 4 scans per 2 minutes (foreground)
 */
data class ScanBudget(
    val scansRemaining: Int,
    val windowResetInSeconds: Int,
    val isThrottled: Boolean,
    val lastScanTimestamp: Long?,
    val windowStartTimestamp: Long
) {
    companion object {
        const val MAX_SCANS_PER_WINDOW = 4
        const val WINDOW_DURATION_MS = 2 * 60 * 1000L
        
        fun initial() = ScanBudget(
            scansRemaining = MAX_SCANS_PER_WINDOW,
            windowResetInSeconds = 0,
            isThrottled = false,
            lastScanTimestamp = null,
            windowStartTimestamp = System.currentTimeMillis()
        )
    }
    
    fun canScan(): Boolean = scansRemaining > 0
}

data class MonitoringConfig(
    val intervalMs: Long = 60_000,
    val modalities: Set<Modality> = setOf(Modality.WIFI, Modality.BLUETOOTH, Modality.CELLULAR),
    val adaptToMotion: Boolean = true,
    val respectThrottle: Boolean = true
)

data class PermissionStatus(
    val granted: Set<Permission>,
    val denied: Set<Permission>,
    val canMeasure: Boolean,
    val degradedModalities: Set<Modality>
)
