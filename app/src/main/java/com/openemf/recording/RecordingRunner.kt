package com.openemf.recording

import com.openemf.data.database.RecordingSessionStatus
import com.openemf.sensors.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

interface RecordingJournal {
    suspend fun begin(request: RecordingRequest)
    suspend fun paused(id: String, paused: Boolean)
    suspend fun finish(id: String, status: RecordingSessionStatus, reason: String)
}

/** One service-owned coroutine tree: pause cancels/joins acquisition; stop cancels the entire tree. */
class RecordingRunner(
    val request: RecordingRequest,
    private val store: RecordingStateStore,
    private val journal: RecordingJournal,
    private val checkRequirements: () -> String?,
    private val measure: suspend () -> Result<Measurement>,
    private val acquireWakeLease: (Long) -> AutoCloseable,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val guardIntervalMs: Long = 1000
) {
    private val pause = MutableStateFlow(false)
    @Volatile private var stopReason: String? = null

    fun pause() {
        if (store.state.value.phase != RecordingPhase.RECORDING) return
        store.update(request.sessionId) { it.copy(phase = RecordingPhase.PAUSING) }
        pause.value = true
    }

    fun resume() {
        if (store.state.value.phase != RecordingPhase.PAUSED) return
        store.update(request.sessionId) { it.copy(phase = RecordingPhase.STARTING) }
        pause.value = false
    }

    fun stop(reason: String = "Stopped by user") {
        stopReason = reason
        store.update(request.sessionId) { it.copy(phase = RecordingPhase.STOPPING) }
    }

    suspend fun run() {
        var status = RecordingSessionStatus.INTERRUPTED
        var began = false
        var reason = "Service ended without a clean stop; end time is unknown"
        try {
            checkRequirements()?.let { throw RecordingFailure(it) }
            journal.begin(request)
            began = true
            coroutineScope {
                launch {
                    while (isActive) {
                        checkRequirements()?.let { throw RecordingFailure(it) }
                        delay(guardIntervalMs)
                    }
                }
                pause.collectLatest { paused ->
                    journal.paused(request.sessionId, paused)
                    store.update(request.sessionId) { it.copy(
                        phase = if (paused) RecordingPhase.PAUSED else RecordingPhase.RECORDING) }
                    if (!paused) while (currentCoroutineContext().isActive) {
                        checkRequirements()?.let { throw RecordingFailure(it) }
                        // Renew a bounded lease each interval; cancellation closes it immediately.
                        acquireWakeLease(request.intervalMs + 60_000).use {
                            val started = clock()
                            val measurement = measure().getOrElse { throw RecordingFailure(it.message ?: "Acquisition or storage failed") }
                            if (measurement.recordingSessionId != request.sessionId || measurement.captureMode != CaptureMode.RECORDING) {
                                throw RecordingFailure("Acquisition returned a capture outside this recording session")
                            }
                            store.update(request.sessionId) { state -> state.copy(
                                savedCaptures = state.savedCaptures + 1, lastCaptureAt = measurement.timestamp) }
                            if (measurement.acquisition.any { (modality, report) -> modality in request.modalities &&
                                    report.status == AcquisitionStatus.PERMISSION_DENIED }) {
                                throw RecordingFailure("A recording scan permission was denied or revoked")
                            }
                            delay((request.intervalMs - (clock() - started)).coerceAtLeast(1000))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (stopReason != null) { status = RecordingSessionStatus.STOPPED; reason = stopReason!! }
            throw e
        } catch (e: Exception) {
            status = RecordingSessionStatus.FAILED
            reason = e.message ?: "Recording failed"
        } finally {
            var journalFailure: String? = null
            if (began) withContext(NonCancellable) {
                try { withTimeout(5000) { journal.finish(request.sessionId, status, reason) } }
                catch (e: Exception) { journalFailure = "Session finalization failed: ${e.message ?: "storage unavailable"}" }
            }
            store.update(request.sessionId) { it.copy(
                phase = if (status == RecordingSessionStatus.STOPPED && journalFailure == null) RecordingPhase.STOPPED else RecordingPhase.ERROR,
                message = journalFailure ?: reason) }
        }
    }

    private class RecordingFailure(message: String) : Exception(message)
}
