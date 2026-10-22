package com.openemf.recording

import com.openemf.data.fixtureMeasurement
import com.openemf.data.database.RecordingSessionStatus
import com.openemf.sensors.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RecordingRunnerTest {
    private class Journal : RecordingJournal {
        val phases = CopyOnWriteArrayList<Boolean>()
        var begun = 0
        var ended: RecordingSessionStatus? = null
        var reason: String? = null
        var finishFails = false
        var beginFails = false
        override suspend fun begin(request: RecordingRequest) { if (beginFails) throw IOException("Database full"); begun++ }
        override suspend fun paused(id: String, paused: Boolean) { phases.add(paused) }
        override suspend fun finish(id: String, status: RecordingSessionStatus, reason: String) {
            if (finishFails) throw IOException("Database full")
            ended = status; this.reason = reason
        }
    }
    private class Rig(val journal: Journal = Journal()) {
        val store = RecordingStateStore().apply { setAppVisible(true) }
        val request = RecordingRequest("session", 1000, Modality.entries.toSet(), setOf(Permission.FINE_LOCATION))
        val held = AtomicInteger()
        val failure = AtomicReference<String?>(null)
        init { check(store.reserve(request)); check(store.consumeStart(request.sessionId) != null) }
        fun capture() = fixtureMeasurement().copy(captureMode = CaptureMode.RECORDING,
            recordingSessionId = request.sessionId, context = MeasurementContext(isForeground = store.appVisible))
        fun runner(measure: suspend () -> Result<Measurement> = { Result.success(capture()) }) = RecordingRunner(
            request, store, journal, { failure.get() }, measure,
            { held.incrementAndGet(); AutoCloseable { held.decrementAndGet() } }, guardIntervalMs = 10)
        suspend fun phase(phase: RecordingPhase) = withTimeout(5000) { store.state.first { it.phase == phase } }
    }

    @Test(timeout = 10000) fun `recording continues without UI collectors then stops and closes its lease`() = runBlocking {
        val rig = Rig()
        val runner = rig.runner()
        val job = launch(Dispatchers.Default) { runner.run() }
        withTimeout(5000) { rig.store.state.first { it.savedCaptures >= 1 } }
        rig.store.setAppVisible(false)
        withTimeout(5000) { rig.store.state.first { it.savedCaptures >= 2 } }
        assertTrue(rig.store.state.value.active)
        runner.stop(); job.cancelAndJoin()
        assertEquals(RecordingSessionStatus.STOPPED, rig.journal.ended)
        assertEquals(RecordingPhase.STOPPED, rig.store.state.value.phase)
        assertEquals(0, rig.held.get())
    }

    @Test(timeout = 10000) fun `pause cancels in-flight acquisition before reporting paused and resume starts new work`() = runBlocking {
        val rig = Rig()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val released = AtomicInteger()
        val runner = rig.runner {
            entered.send(Unit)
            try { awaitCancellation() } finally { released.incrementAndGet() }
        }
        val job = launch(Dispatchers.Default) { runner.run() }
        withTimeout(5000) { entered.receive() }
        runner.pause(); rig.phase(RecordingPhase.PAUSED)
        assertEquals(1, released.get())
        assertEquals(0, rig.held.get())
        assertEquals(0L, rig.store.state.value.savedCaptures)
        runner.resume()
        withTimeout(5000) { entered.receive() }
        runner.stop(); job.cancelAndJoin()
        assertEquals(2, released.get())
        assertEquals(0, rig.held.get())
        assertEquals(listOf(false, true, false), rig.journal.phases.toList())
    }

    @Test(timeout = 10000) fun `requirements are checked while paused and loss ends the session`() = runBlocking {
        val rig = Rig()
        val runner = rig.runner()
        val job = launch(Dispatchers.Default) { runner.run() }
        withTimeout(5000) { rig.store.state.first { it.savedCaptures == 1L } }
        runner.pause(); rig.phase(RecordingPhase.PAUSED)
        rig.failure.set("Recording permission was revoked")
        withTimeout(5000) { job.join() }
        assertEquals(RecordingSessionStatus.FAILED, rig.journal.ended)
        assertEquals(RecordingPhase.ERROR, rig.store.state.value.phase)
        assertTrue(rig.store.state.value.message!!.contains("revoked"))
        assertEquals(0, rig.held.get())
    }

    @Test(timeout = 10000) fun `acquisition or persistence failure stops instead of silently retrying indefinitely`() = runBlocking {
        val rig = Rig()
        rig.runner { Result.failure(IOException("Storage full")) }.run()
        assertEquals(RecordingSessionStatus.FAILED, rig.journal.ended)
        assertEquals(0L, rig.store.state.value.savedCaptures)
        assertEquals("Storage full", rig.store.state.value.message)
        assertEquals(0, rig.held.get())
    }

    @Test(timeout = 10000) fun `a persisted denied scan is counted then recording stops`() = runBlocking {
        val rig = Rig()
        rig.runner { Result.success(rig.capture().copy(acquisition = mapOf(Modality.CELLULAR to
            AcquisitionReport(AcquisitionStatus.PERMISSION_DENIED)))) }.run()
        assertEquals(1L, rig.store.state.value.savedCaptures)
        assertEquals(RecordingSessionStatus.FAILED, rig.journal.ended)
        assertEquals(0, rig.held.get())
    }

    @Test(timeout = 10000) fun `unexpected service cancellation is interrupted and releases acquisition and lease`() = runBlocking {
        val rig = Rig()
        val entered = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val runner = rig.runner {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned.complete(Unit) }
        }
        val job = launch(Dispatchers.Default) { runner.run() }
        withTimeout(5000) { entered.await() }
        job.cancelAndJoin()
        assertTrue(cleaned.isCompleted)
        assertEquals(RecordingSessionStatus.INTERRUPTED, rig.journal.ended)
        assertEquals(0, rig.held.get())
        assertFalse(rig.store.state.value.active)
    }

    @Test(timeout = 10000) fun `failed session finalization still stops work and surfaces an error`() = runBlocking {
        val rig = Rig(Journal().apply { finishFails = true })
        val runner = rig.runner()
        val job = launch(Dispatchers.Default) { runner.run() }
        withTimeout(5000) { rig.store.state.first { it.savedCaptures == 1L } }
        runner.stop(); job.cancelAndJoin()
        assertEquals(RecordingPhase.ERROR, rig.store.state.value.phase)
        assertTrue(rig.store.state.value.message!!.contains("finalization failed"))
        assertEquals(0, rig.held.get())
    }

    @Test(timeout = 10000) fun `failed journal creation never acquires a wake lease or captures`() = runBlocking {
        val rig = Rig(Journal().apply { beginFails = true })
        var captured = false
        rig.runner { captured = true; Result.success(rig.capture()) }.run()
        assertFalse(captured)
        assertEquals(0, rig.held.get())
        assertEquals("Database full", rig.store.state.value.message)
        assertNull(rig.journal.ended)
    }

    @Test(timeout = 10000) fun `a capture from another mode or session is rejected`() = runBlocking {
        val rig = Rig()
        rig.runner { Result.success(fixtureMeasurement()) }.run()
        assertEquals(0L, rig.store.state.value.savedCaptures)
        assertEquals(RecordingSessionStatus.FAILED, rig.journal.ended)
        assertTrue(rig.store.state.value.message!!.contains("outside this recording session"))
    }
}
