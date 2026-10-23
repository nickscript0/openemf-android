package com.openemf.ui

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.openemf.data.database.OpenEMFDatabase
import com.openemf.data.fixtureMeasurement
import com.openemf.data.fixtureTime
import com.openemf.data.repository.MeasurementRepository
import com.openemf.recording.RecordingStateStore
import com.openemf.sensors.api.SourceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class ChartStorageTest {
    private lateinit var db: OpenEMFDatabase
    private lateinit var repository: MeasurementRepository
    private val name = "chart-${UUID.randomUUID()}"
    private fun open() {
        db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), OpenEMFDatabase::class.java, name).build()
        repository = MeasurementRepository(db, db.measurementDao(), db.placeDao(), db.observationDao(), RecordingStateStore())
    }
    @Before fun setup() = open()
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }

    @Test(timeout = 60000) fun boundedChartWindowSurvivesReopenAndIsIndependentOfRawPager() = runBlocking {
        db.withTransaction {
            repeat(2005) { index ->
                val m = fixtureMeasurement("capture-$index")
                repository.saveMeasurement(m.copy(timestamp = fixtureTime.plusSeconds(index.toLong()),
                    bluetoothSources = emptyList(), cellularSources = emptyList()))
            }
        }
        db.close(); open()
        val rows = repository.observeSourceChart(SourceType.WIFI, "wifi:aa", fixtureTime).first()
        assertEquals(2001, rows.size) // One overflow row lets the UI disclose its 2,000-row bound.
        assertEquals("capture-2004", rows.first().observation.measurementId)
        assertEquals("capture-4", rows.last().observation.measurementId)
        val page = repository.getSourceHistory(SourceType.WIFI, "wifi:aa", 100)
        assertEquals(100, page.size)
        val older = repository.getSourceHistory(SourceType.WIFI, "wifi:aa", 100, page.last().observation.rowId)
        assertEquals(100, older.size)
        assertEquals(rows.map { it.observation.rowId },
            repository.observeSourceChart(SourceType.WIFI, "wifi:aa", fixtureTime).first().map { it.observation.rowId })
        assertTrue(repository.observeSourceChart(SourceType.BLUETOOTH, "wifi:aa", fixtureTime).first().isEmpty())
        assertEquals(5, repository.observeSourceChart(SourceType.WIFI, "wifi:aa", fixtureTime.plusSeconds(2000)).first().size)
    }

    @OptIn(FlowPreview::class)
    @Test(timeout = 30000) fun chartFlowUpdatesForNewCapturesAndDeletions() = runBlocking {
        val updates = repository.observeSourceChart(SourceType.WIFI, "wifi:aa", fixtureTime.minusSeconds(1)).produceIn(this)
        try {
            assertTrue(withTimeout(5000) { updates.receive() }.isEmpty())
            repository.saveMeasurement(fixtureMeasurement())
            assertEquals(1, withTimeout(5000) { updates.receive() }.size)
            repository.deleteMeasurement("capture")
            assertTrue(withTimeout(5000) { updates.receive() }.isEmpty())
        } finally { updates.cancel() }
    }
}
