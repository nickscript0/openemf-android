package com.openemf.recording

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.openemf.data.database.*
import com.openemf.data.fixtureMeasurement
import com.openemf.data.repository.MeasurementRepository
import com.openemf.data.repository.RecordingSessionRepository
import com.openemf.sensors.api.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class RecordingJournalTest {
    private lateinit var context: Context
    private lateinit var database: OpenEMFDatabase
    private lateinit var journal: RecordingSessionRepository
    private lateinit var measurements: MeasurementRepository
    private lateinit var name: String
    private val state = RecordingStateStore()
    private val request = RecordingRequest("session", 60000, Modality.entries.toSet(), setOf(Permission.FINE_LOCATION))
    @Before fun open() { context = RuntimeEnvironment.getApplication(); name = "recording-${UUID.randomUUID()}.db"; reopen() }
    private fun reopen() {
        database = Room.databaseBuilder(context, OpenEMFDatabase::class.java, name).build()
        journal = RecordingSessionRepository(database)
        measurements = MeasurementRepository(database, database.measurementDao(), database.placeDao(), database.observationDao(), state)
    }
    @After fun close() { database.close(); context.deleteDatabase(name) }

    @Test(timeout = 30000) fun `recorded captures and raw export keep session identity after UI-independent saves`() = runBlocking {
        journal.begin(request)
        val m = fixtureMeasurement().copy(captureMode = CaptureMode.RECORDING, recordingSessionId = request.sessionId,
            context = MeasurementContext(isForeground = false))
        measurements.saveMeasurement(m)
        val summary = journal.recent.first().single()
        assertEquals(RecordingSessionStatus.RECORDING, summary.session.status)
        assertEquals(1L, summary.captureCount)
        assertEquals(m.timestamp, summary.lastCaptureAt)
        val raw = measurements.getSourceHistory(SourceType.WIFI, "wifi:aa").single()
        assertEquals(request.sessionId, raw.recordingSessionId)
        assertFalse(raw.isForeground)
        assertEquals(CaptureMode.RECORDING, raw.captureMode)
        val writer = StringWriter()
        assertEquals(3L, measurements.exportRawObservations(writer))
        assertTrue(writer.toString().contains("RECORDING,false,UNKNOWN,session,"))
    }

    @Test(timeout = 30000) fun `unclean process ending is reconciled as interrupted with unknown end time`() = runBlocking {
        journal.begin(request)
        measurements.saveMeasurement(fixtureMeasurement().copy(recordingSessionId = request.sessionId, captureMode = CaptureMode.RECORDING))
        journal.paused(request.sessionId, true)
        database.close(); reopen()
        val recovered = journal.recent.first().single()
        assertEquals(RecordingSessionStatus.INTERRUPTED, recovered.session.status)
        assertNull(recovered.session.endedAt)
        assertEquals(1L, recovered.captureCount)
        assertFalse(RecordingStateStore().state.value.active)
    }

    @Test(timeout = 30000) fun `pause and clean stop survive reopen without being mistaken for a running recorder`() = runBlocking {
        journal.begin(request)
        journal.paused(request.sessionId, true)
        assertEquals(RecordingSessionStatus.PAUSED, journal.recent.first().single().session.status)
        journal.paused(request.sessionId, false)
        journal.finish(request.sessionId, RecordingSessionStatus.STOPPED, "Stopped by user")
        database.close(); reopen()
        val session = journal.recent.first().single().session
        assertEquals(RecordingSessionStatus.STOPPED, session.status)
        assertNotNull(session.endedAt)
        assertEquals("Stopped by user", session.reason)
    }

    @Test(timeout = 30000) fun `a new session reconciles an unfinished prior journal without resuming it`() = runBlocking {
        journal.begin(request)
        journal.begin(request.copy(sessionId = "new-session"))
        assertEquals(RecordingSessionStatus.INTERRUPTED, database.recordingSessionDao().get(request.sessionId)!!.status)
        assertEquals(RecordingSessionStatus.RECORDING, database.recordingSessionDao().get("new-session")!!.status)
    }

    @Test(timeout = 30000) fun `clear history also removes ended recording journals`() = runBlocking {
        journal.begin(request)
        journal.finish(request.sessionId, RecordingSessionStatus.STOPPED, "Stopped by user")
        measurements.deleteAllMeasurements()
        assertTrue(journal.recent.first().isEmpty())
    }
    @Test(timeout = 30000) fun `history clear is rejected atomically while recording is starting or active`() = runBlocking {
        journal.begin(request)
        measurements.saveMeasurement(fixtureMeasurement().copy(recordingSessionId = request.sessionId, captureMode = CaptureMode.RECORDING))
        state.setAppVisible(true)
        assertTrue(state.reserve(request))
        try { measurements.deleteAllMeasurements(); fail("Expected active recording guard") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Stop recording")) }
        assertEquals(1L, journal.recent.first().single().captureCount)
    }

}
