package com.openemf.sensors.magnetic

import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Magnetic field scanner using the device's magnetometer.
 *
 * IMPORTANT LIMITATIONS:
 * - Measures total magnetic field strength (Earth's field + local sources)
 * - Earth's field is ~25-65 µT depending on location
 * - Cannot separate 50/60 Hz AC fields from DC fields without signal processing
 * - Phone magnetometers are not calibrated for precise EMF measurement
 * - Results should be interpreted as relative indicators, not absolute values
 *
 * This measures LOW FREQUENCY magnetic fields (ELF-EMF) from:
 * - Power lines
 * - Electrical wiring
 * - Appliances (motors, transformers)
 * - NOT the same as RF-EMF from WiFi/cellular
 */
@Singleton
class MagneticScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    private val magnetometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    // Uncalibrated magnetometer can show raw values without bias correction
    private val magnetometerUncalibrated: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    }

    companion object {
        // Earth's magnetic field typical range (µT)
        private const val EARTH_FIELD_MIN = 25.0
        private const val EARTH_FIELD_MAX = 65.0
        private const val EARTH_FIELD_TYPICAL = 50.0

        // Measurement duration for averaging
        private const val SAMPLE_DURATION_MS = 2000L
        private const val SAMPLE_RATE_US = 20000 // 50 Hz sampling

        // Threshold for "elevated" field (above typical Earth + margin)
        const val ELEVATED_THRESHOLD_UT = 70.0
    }

    /**
     * Check if magnetometer is available.
     */
    fun isAvailable(): Boolean {
        return magnetometer != null
    }

    /**
     * Perform a magnetic field measurement.
     * Takes multiple samples over 2 seconds and returns statistics.
     */
    suspend fun measure(): Result<MagneticReading> = withTimeoutOrNull(3000) { measureSamples() }
        ?: Result.failure(MagneticScanException("Magnetometer timed out"))

    private suspend fun measureSamples(): Result<MagneticReading> = suspendCancellableCoroutine { continuation ->
        val sensor = magnetometer
        val manager = sensorManager

        if (sensor == null || manager == null) {
            continuation.resume(Result.failure(MagneticScanException("Magnetometer not available")))
            return@suspendCancellableCoroutine
        }

        val samples = mutableListOf<FloatArray>()
        val startTime = SystemClock.elapsedRealtime()
        val completed = AtomicBoolean(false)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && !completed.get()) {
                    samples.add(event.values.copyOf())

                    // Check if we've collected enough samples
                    if (SystemClock.elapsedRealtime() - startTime >= SAMPLE_DURATION_MS && completed.compareAndSet(false, true)) {
                        manager.unregisterListener(this)

                        if (samples.isEmpty()) {
                            continuation.resume(Result.failure(MagneticScanException("No samples collected")))
                        } else {
                            val reading = processsamples(samples)
                            continuation.resume(Result.success(reading))
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Track accuracy for confidence calculation
            }
        }

        val registered = manager.registerListener(
            listener,
            sensor,
            SAMPLE_RATE_US
        )

        if (!registered && completed.compareAndSet(false, true)) {
            continuation.resume(Result.failure(MagneticScanException("Failed to register sensor listener")))
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            completed.set(true)
            manager.unregisterListener(listener)
        }
    }

    /**
     * Observe magnetic field continuously as a Flow.
     */
    fun observeMagnetic(): Flow<MagneticReading> = callbackFlow {
        val sensor = magnetometer
        val manager = sensorManager

        if (sensor == null || manager == null) {
            close()
            return@callbackFlow
        }

        val recentSamples = mutableListOf<FloatArray>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    recentSamples.add(event.values.copyOf())

                    // Keep last 50 samples (~1 second at 50Hz)
                    while (recentSamples.size > 50) {
                        recentSamples.removeAt(0)
                    }

                    // Emit reading every 10 samples
                    if (recentSamples.size >= 10 && recentSamples.size % 10 == 0) {
                        val reading = processsamples(recentSamples.toList())
                        trySend(reading)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        manager.registerListener(listener, sensor, SAMPLE_RATE_US)

        awaitClose {
            manager.unregisterListener(listener)
        }
    }

    /**
     * Get a quick single reading (instant, no averaging).
     */
    suspend fun getInstantReading(): MagneticReading? = withTimeoutOrNull(2000) { awaitInstantReading() }

    private suspend fun awaitInstantReading(): MagneticReading? = suspendCancellableCoroutine { continuation ->
        val sensor = magnetometer
        val manager = sensorManager

        if (sensor == null || manager == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val completed = AtomicBoolean(false)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && completed.compareAndSet(false, true)) {
                    manager.unregisterListener(this)

                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()
                    val magnitude = sqrt(x * x + y * y + z * z)

                    val reading = MagneticReading(
                        magnitudeUt = magnitude,
                        xUt = x,
                        yUt = y,
                        zUt = z,
                        minUt = magnitude,
                        maxUt = magnitude,
                        stdDevUt = 0.0,
                        sampleCount = 1,
                        isElevated = magnitude > ELEVATED_THRESHOLD_UT,
                        estimatedAcComponent = 0.0,
                        confidence = 0.5f // Lower confidence for single reading
                    )

                    continuation.resume(reading)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (!registered && completed.compareAndSet(false, true)) continuation.resume(null)

        continuation.invokeOnCancellation {
            completed.set(true)
            manager.unregisterListener(listener)
        }
    }

    /**
     * Process collected samples into a MagneticReading.
     */
    private fun processsamples(samples: List<FloatArray>): MagneticReading {
        val magnitudes = samples.map { values ->
            val x = values[0].toDouble()
            val y = values[1].toDouble()
            val z = values[2].toDouble()
            sqrt(x * x + y * y + z * z)
        }

        val avgMagnitude = magnitudes.average()
        val minMagnitude = magnitudes.minOrNull() ?: 0.0
        val maxMagnitude = magnitudes.maxOrNull() ?: 0.0

        // Calculate standard deviation (indicates AC component/fluctuation)
        val variance = magnitudes.map { (it - avgMagnitude) * (it - avgMagnitude) }.average()
        val stdDev = sqrt(variance)

        // Average x, y, z components
        val avgX = samples.map { it[0].toDouble() }.average()
        val avgY = samples.map { it[1].toDouble() }.average()
        val avgZ = samples.map { it[2].toDouble() }.average()

        // Estimate AC component as the fluctuation (std dev * 2 for peak-to-peak approximation)
        // This is a rough estimate - proper AC detection would need FFT analysis
        val estimatedAc = stdDev * 2

        // Confidence based on sample count and consistency
        val confidence = when {
            samples.size < 10 -> 0.3f
            samples.size < 50 -> 0.6f
            stdDev > 20 -> 0.5f // High fluctuation reduces confidence
            else -> 0.9f
        }

        return MagneticReading(
            magnitudeUt = avgMagnitude,
            xUt = avgX,
            yUt = avgY,
            zUt = avgZ,
            minUt = minMagnitude,
            maxUt = maxMagnitude,
            stdDevUt = stdDev,
            sampleCount = samples.size,
            isElevated = avgMagnitude > ELEVATED_THRESHOLD_UT,
            estimatedAcComponent = estimatedAc,
            confidence = confidence
        )
    }
}

/**
 * Magnetic field reading result.
 *
 * @param magnitudeUt Total field strength in microtesla (µT)
 * @param xUt X-axis component in µT
 * @param yUt Y-axis component in µT
 * @param zUt Z-axis component in µT
 * @param minUt Minimum magnitude during measurement
 * @param maxUt Maximum magnitude during measurement
 * @param stdDevUt Standard deviation (indicates AC/fluctuation)
 * @param sampleCount Number of samples collected
 * @param isElevated True if field exceeds typical Earth field + margin
 * @param estimatedAcComponent Rough estimate of AC field component (from fluctuation)
 * @param confidence Confidence in reading (0-1)
 */
data class MagneticReading(
    val magnitudeUt: Double,
    val xUt: Double,
    val yUt: Double,
    val zUt: Double,
    val minUt: Double,
    val maxUt: Double,
    val stdDevUt: Double,
    val sampleCount: Int,
    val isElevated: Boolean,
    val estimatedAcComponent: Double,
    val confidence: Float
) {
    /**
     * Get field strength relative to Earth's typical field.
     * 1.0 = Earth's field, 2.0 = double Earth's field
     */
    val relativeToEarth: Double
        get() = magnitudeUt / 50.0

    /**
     * Estimate contribution from local sources (above Earth's field).
     * This is very rough - Earth's field varies by location.
     */
    val estimatedLocalFieldUt: Double
        get() = (magnitudeUt - 50.0).coerceAtLeast(0.0)

    /**
     * Convert to milligauss (1 µT = 10 mG).
     */
    val magnitudeMg: Double
        get() = magnitudeUt * 10.0
}

class MagneticScanException(message: String) : Exception(message)
