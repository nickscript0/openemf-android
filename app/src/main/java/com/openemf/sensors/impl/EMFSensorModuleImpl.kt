package com.openemf.sensors.impl

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.openemf.sensors.api.*
import com.openemf.sensors.acquisition.acquireRadio
import com.openemf.sensors.bluetooth.BluetoothScanner
import com.openemf.sensors.cellular.CellularScanner
import com.openemf.sensors.magnetic.MagneticScanner
import com.openemf.sensors.scheduling.ScanScheduler
import com.openemf.sensors.scoring.EScoreCalculator
import com.openemf.sensors.wifi.WifiScanner
import com.openemf.data.repository.MeasurementRepository
import com.openemf.recording.RecordingStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EMFSensorModuleImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiScanner: WifiScanner,
    private val bluetoothScanner: BluetoothScanner,
    private val cellularScanner: CellularScanner,
    private val magneticScanner: MagneticScanner,
    private val scheduler: ScanScheduler,
    private val calculator: EScoreCalculator,
    private val repository: MeasurementRepository,
    private val recording: RecordingStateStore
) : EMFSensorModule {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scanMutex = Mutex()
    private val measurementContext = MutableStateFlow(MeasurementContext(isForeground = false))
    @Volatile private var monitoringJob: Job? = null
    private val _monitoringActive = MutableStateFlow(false)
    override val monitoringActive = _monitoringActive.asStateFlow()
    private val _sensorState = MutableStateFlow<SensorState>(SensorState.Idle)
    override val sensorState = _sensorState.asStateFlow()
    private val _scanBudget = MutableStateFlow(scheduler.getCurrentBudget())
    override val scanBudget = _scanBudget.asStateFlow()
    private val _permissionStatus = MutableStateFlow(readPermissions())
    override val permissionStatus = _permissionStatus.asStateFlow()
    private val _measurements = MutableSharedFlow<Measurement>(replay = 1)
    override val measurements = _measurements.asSharedFlow()

    init {
        scope.launch {
            while (isActive) {
                _scanBudget.value = scheduler.getCurrentBudget()
                checkPermissions()
                delay(1000)
            }
        }
    }

    override suspend fun measureNow(): Result<Measurement> = measure(setOf(Modality.WIFI, Modality.BLUETOOTH, Modality.CELLULAR), CaptureMode.MANUAL)

    override suspend fun record(sessionId: String, modalities: Set<Modality>): Result<Measurement> =
        measure(modalities.intersect(recording.state.value.modalities), CaptureMode.RECORDING, sessionId)

    private suspend fun measure(modalities: Set<Modality>, mode: CaptureMode, sessionId: String? = null): Result<Measurement> = scanMutex.withLock {
        val permissions = checkPermissions()
        val serviceRecording = sessionId != null && mode == CaptureMode.RECORDING && recording.canAcquire(sessionId)
        if (mode == CaptureMode.RECORDING && !serviceRecording) {
            return@withLock Result.failure(MeasurementException("Recording session is not active"))
        }
        if (!serviceRecording && (recording.state.value.active || !measurementContext.value.isForeground)) {
            return@withLock Result.failure(MeasurementException("Open the app and stop recording before a manual or live-refresh scan"))
        }
        if (!permissions.canMeasure) {
            _sensorState.value = SensorState.PermissionRequired(permissions.denied.toList(), false)
            return@withLock Result.failure(MeasurementException("Scan permissions required"))
        }
        val started = SystemClock.elapsedRealtime()
        _sensorState.value = SensorState.Scanning(Instant.now(), modalities)
        try {
            val measurement = coroutineScope {
                val wifi = async {
                    if (Modality.WIFI !in modalities) notRequested<WifiSource>()
                    else acquireRadio(15_000) {
                        // A Wi-Fi limit never suppresses independent Bluetooth/cellular acquisition.
                        if (!wifiScanner.hasPermissions() || !wifiScanner.isAvailable()) wifiScanner.scan()
                        else if (scheduler.tryAcquireScan(measurementContext.value.isForeground || serviceRecording)) wifiScanner.scan()
                        else AcquisitionResult(wifiScanner.getCachedResults(), AcquisitionStatus.THROTTLED,
                            "Wi-Fi scan budget unavailable; retry in ${(scheduler.getTimeUntilNextScan(measurementContext.value.isForeground || serviceRecording) + 999) / 1000}s")
                    }
                }
                val bluetooth = async {
                    if (Modality.BLUETOOTH !in modalities) notRequested<BluetoothSource>()
                    else acquireRadio(10_000) { bluetoothScanner.scan() }
                }
                val cellular = async {
                    if (Modality.CELLULAR !in modalities) notRequested<CellularSource>()
                    else acquireRadio(10_000) { cellularScanner.scan() }
                }
                val magnetic = async { scanMagnetic() }
                val w = wifi.await()
                val b = bluetooth.await()
                val c = cellular.await()
                val magneticReading = magnetic.await()
                val now = SystemClock.elapsedRealtime()
                val score = calculator.calculateFresh(w.sources + b.sources + c.sources, now)
                val wc = coverage(w, now)
                val bc = coverage(b, now)
                val cc = coverage(c, now)
                Measurement(
                    id = UUID.randomUUID().toString(), timestamp = Instant.now(), eScore = score,
                    wifiSources = w.sources, bluetoothSources = b.sources, cellularSources = c.sources,
                    magneticField = magneticReading, context = measurementContext.value,
                    confidence = ConfidenceFlags(wc, bc, cc, (wc + bc + cc) / 3),
                    acquisition = mapOf(Modality.WIFI to w.report, Modality.BLUETOOTH to b.report, Modality.CELLULAR to c.report),
                    pairedBluetoothDevices = if (Modality.BLUETOOTH in modalities) bluetoothScanner.getPairedDevices() else emptyList(),
                    capturedElapsedRealtimeMs = now, captureMode = mode,
                    recordingSessionId = sessionId,
                    bootCount = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull())
            }
            // Saving belongs to acquisition, not a UI collector. Complete only after the atomic save.
            currentCoroutineContext().ensureActive()
            if (serviceRecording && !recording.canAcquire(sessionId)) throw CancellationException("Recording paused or stopped")
            try { repository.saveMeasurement(measurement) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw MeasurementException("Could not save measurement: ${e.message ?: "storage unavailable"}") }
            _measurements.emit(measurement)
            _scanBudget.value = scheduler.getCurrentBudget()
            _sensorState.value = SensorState.Complete(measurement, measurement.confidence, SystemClock.elapsedRealtime() - started)
            Result.success(measurement)
        } catch (e: CancellationException) {
            _sensorState.value = SensorState.Idle
            throw e
        } catch (e: Exception) {
            _sensorState.value = SensorState.Error(ErrorType.SCAN_FAILED, e.message ?: "Acquisition failed", true)
            Result.failure(e)
        }
    }

    /** Foreground live refresh only. Durable foreground-service recording is implemented separately. */
    override fun startMonitoring(config: MonitoringConfig) {
        if (recording.state.value.active) return
        monitoringJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                if (measurementContext.value.isForeground) {
                    measure(config.modalities, CaptureMode.LIVE_REFRESH)
                    delay(config.intervalMs.coerceAtLeast(10_000))
                } else delay(1000)
            }
        }
        monitoringJob = job
        _monitoringActive.value = true
        job.invokeOnCompletion { if (monitoringJob === job) _monitoringActive.value = false }
        job.start()
    }

    override fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        _monitoringActive.value = false
    }
    override fun updateContext(context: MeasurementContext) { measurementContext.value = context }
    override fun setForeground(isForeground: Boolean) { measurementContext.update { it.copy(isForeground = isForeground) } }
    override fun checkPermissions(): PermissionStatus = readPermissions().also { _permissionStatus.value = it }

    private fun readPermissions(): PermissionStatus = PermissionPolicy.evaluate(Build.VERSION.SDK_INT,
        PermissionPolicy.required(Build.VERSION.SDK_INT).filterTo(mutableSetOf()) {
            ContextCompat.checkSelfPermission(context, it.androidName) == PackageManager.PERMISSION_GRANTED
        })

    private suspend fun scanMagnetic(): MagneticFieldReading? = try {
        withTimeoutOrNull(3_000) {
            magneticScanner.measure().getOrNull()?.let {
                MagneticFieldReading(it.magnitudeUt, it.magnitudeMg, it.isElevated, it.estimatedAcComponent, it.confidence)
            }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private fun <T : Source> notRequested() = AcquisitionResult<T>(emptyList(), AcquisitionStatus.NOT_REQUESTED)

    private fun coverage(result: AcquisitionResult<out Source>, now: Long): Float {
        if (result.sources.isEmpty()) return if (result.report.status == AcquisitionStatus.UPDATED) 1f else 0f
        return result.sources.count { it.rssi != null && it.observation.freshness(now) == Freshness.FRESH }.toFloat() / result.sources.size
    }
}

class MeasurementException(message: String) : Exception(message)
