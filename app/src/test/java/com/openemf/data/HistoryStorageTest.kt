package com.openemf.data

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.openemf.data.database.*
import com.openemf.data.export.RawCsvExporter
import com.openemf.data.repository.MeasurementRepository
import com.openemf.sensors.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryStorageTest {
    private lateinit var context: Context
    private lateinit var database: OpenEMFDatabase
    private lateinit var repository: MeasurementRepository
    private lateinit var databaseName: String

    @Before fun open() {
        context = RuntimeEnvironment.getApplication()
        databaseName = "history-${UUID.randomUUID()}.db"
        reopen()
    }
    private fun reopen() {
        database = Room.databaseBuilder(context, OpenEMFDatabase::class.java, databaseName).build()
        repository = MeasurementRepository(database, database.measurementDao(), database.placeDao(), database.observationDao())
    }
    @After fun close() { database.close(); context.deleteDatabase(databaseName) }

    @Test(timeout = 30000) fun `fresh schema persists all radio fields and provenance across reopen`() = runBlocking {
        val measurement = fixtureMeasurement().copy(captureMode = CaptureMode.RECORDING, recordingSessionId = "session-1")
        repository.saveMeasurement(measurement)
        database.close()
        reopen()
        val rows = repository.getObservations(measurement.id).first()
        assertEquals(3, rows.size) // Paired-only inventory never becomes an observation.
        val wifi = rows.single { it.sourceType == SourceType.WIFI }
        assertEquals(measurement.wifiSources.single().observation.timestamp, wifi.observedAt)
        assertEquals(199_000L, wifi.observedElapsedRealtimeMs)
        assertEquals(1_000L, wifi.ageAtCaptureMs)
        assertEquals(Freshness.FRESH, wifi.freshnessAtCapture)
        assertTrue(wifi.cached)
        assertEquals(5955, wifi.wifi!!.frequencyMhz)
        assertEquals(WifiChannelWidth.WIDTH_160_MHZ, wifi.wifi.channelWidth)
        val cell = rows.single { it.sourceType == SourceType.CELLULAR }
        assertEquals(68_719_476_735L, cell.cellular!!.cellId)
        assertEquals(measurement.cellularSources.single().details, cell.cellular.details)
        assertEquals(Freshness.STALE, cell.freshnessAtCapture)
        assertEquals("SS_RSRP", cell.signalMetric)
        val bluetooth = rows.single { it.sourceType == SourceType.BLUETOOTH }
        assertNull(bluetooth.signalDbm)
        assertNull(bluetooth.observedAt)
        assertNull(bluetooth.ageAtCaptureMs)
        assertNull(bluetooth.bluetooth!!.connected)
        assertEquals(Freshness.UNKNOWN, bluetooth.freshnessAtCapture)
        val record = repository.getSourceHistory(SourceType.CELLULAR, "cell:nr").single()
        assertEquals("session-1", record.recordingSessionId)
        assertEquals(CaptureMode.RECORDING, record.captureMode)
        assertEquals(42, record.bootCount)
        assertEquals(200_000L, record.captureElapsedRealtimeMs)
        assertTrue(record.isForeground)
        assertEquals(MotionState.UNKNOWN, record.motionState)
        assertEquals(AcquisitionStatus.UPDATED, record.acquisitionStatus)
    }

    @Test(timeout = 30000) fun `statistics and history cover more than the last 100 captures without replay duplicates`() = runBlocking {
        repeat(140) { index ->
            repository.saveMeasurement(fixtureMeasurement("capture-$index", if (index < 40) 90 else 10)
                .copy(timestamp = fixtureTime.plusMillis(index.toLong())))
        }
        val unavailable = fixtureMeasurement("no-index").let { it.copy(eScore = it.eScore.copy(sourceCount = 0)) }
        repository.saveMeasurement(unavailable)
        repository.saveMeasurement(unavailable)
        database.close()
        reopen()
        val history = repository.getAllMeasurements().first()
        assertEquals(141, history.size)
        val stats = repository.getStatistics()
        assertEquals(141, stats.totalCount)
        assertEquals(140, stats.scoredCount)
        assertEquals(32, stats.averageScore)
        assertEquals(10, stats.minScore)
        assertEquals(90, stats.maxScore)
        val sources = repository.getSources().first()
        assertEquals(3, sources.size)
        assertTrue(sources.all { it.captureCount == 141L })
    }

    @Test(timeout = 30000) fun `keyset source history retains cache times across pages and concurrent new captures`() = runBlocking {
        repeat(5) { index -> repository.saveMeasurement(fixtureMeasurement("capture-$index").copy(
            timestamp = fixtureTime.plusMillis(index * 50_000L), capturedElapsedRealtimeMs = 200_000 + index * 50_000L)) }
        val page1 = repository.getSourceHistory(SourceType.WIFI, "wifi:aa", 2)
        repository.saveMeasurement(fixtureMeasurement("new-capture"))
        val page2 = repository.getSourceHistory(SourceType.WIFI, "wifi:aa", 2, page1.last().observation.rowId)
        val page3 = repository.getSourceHistory(SourceType.WIFI, "wifi:aa", 2, page2.last().observation.rowId)
        val older = page1 + page2 + page3
        assertEquals(5, older.size)
        assertEquals(5, older.map { it.observation.rowId }.distinct().size)
        assertEquals(listOf("capture-4", "capture-3", "capture-2", "capture-1", "capture-0"), older.map { it.observation.measurementId })
        assertTrue(older.all { it.observation.observedElapsedRealtimeMs == 199_000L })
        assertEquals(listOf(201_000L, 151_000L, 101_000L, 51_000L, 1_000L), older.map { it.observation.ageAtCaptureMs })
        assertEquals(listOf(Freshness.STALE, Freshness.STALE, Freshness.FRESH, Freshness.FRESH, Freshness.FRESH),
            older.map { it.observation.freshnessAtCapture })
    }

    @Test(timeout = 30000) fun `invalid observation save rolls back the whole capture`() = runBlocking {
        val measurement = fixtureMeasurement()
        try {
            repository.saveMeasurement(measurement.copy(wifiSources = measurement.wifiSources + measurement.wifiSources))
            fail("Expected duplicate source constraint to reject capture")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(0, database.measurementDao().getTotalCount())
        assertNull(database.observationDao().getCapture(measurement.id))
        assertTrue(database.observationDao().getAcquisitions(measurement.id).isEmpty())
        assertEquals(0L, database.observationDao().maxRowId())
    }

    @Test(timeout = 30000) fun `deletion cascades through observations and acquisition metadata and clear resets history`() = runBlocking {
        repository.saveMeasurement(fixtureMeasurement("one"))
        repository.saveMeasurement(fixtureMeasurement("two"))
        repository.deleteMeasurement("one")
        assertNull(database.observationDao().getCapture("one"))
        assertTrue(repository.getObservations("one").first().isEmpty())
        assertTrue(database.observationDao().getAcquisitions("one").isEmpty())
        assertEquals(3, repository.getObservations("two").first().size)
        repository.deleteAllMeasurements()
        assertTrue(repository.getAllMeasurements().first().isEmpty())
        assertTrue(repository.getSources().first().isEmpty())
        assertEquals(0L, database.observationDao().maxRowId())
        assertNull(repository.getStatistics().averageScore)
    }

    @Test(timeout = 30000) fun `failed empty acquisitions retain their reason without fabricating observations`() = runBlocking {
        val m = fixtureMeasurement().let { it.copy(wifiSources = emptyList(), bluetoothSources = emptyList(), cellularSources = emptyList(),
            eScore = it.eScore.copy(sourceCount = 0), acquisition = mapOf(Modality.WIFI to
                AcquisitionReport(AcquisitionStatus.PERMISSION_DENIED, "Permission revoked"))) }
        repository.saveMeasurement(m)
        assertEquals(1, repository.getAllMeasurements().first().size)
        assertEquals(false, repository.getAllMeasurements().first().single().indexAvailable)
        assertTrue(repository.getSources().first().isEmpty())
        val reports = repository.getAcquisitions(m.id)
        assertEquals(3, reports.size)
        assertEquals("Permission revoked", reports.single { it.modality == Modality.WIFI }.detail)
        val out = StringWriter()
        assertEquals(0L, repository.exportRawObservations(out))
        assertEquals(1, out.toString().lines().filter { it.isNotEmpty() }.size)
    }

    @Test(timeout = 30000) fun `raw export crosses bounded page boundaries with exact row count and stable ordering`() = runBlocking {
        val m = fixtureMeasurement()
        val signals = (1..503).map { m.wifiSources.single().copy(id = "wifi-$it", bssid = "wifi-$it") }
        repository.saveMeasurement(m.copy(wifiSources = signals, bluetoothSources = emptyList(), cellularSources = emptyList()))
        val out = StringWriter()
        assertEquals(503L, repository.exportRawObservations(out))
        val lines = out.toString().split("\r\n").filter { it.isNotEmpty() }
        assertEquals(504, lines.size)
        assertTrue(lines[1].startsWith("1,capture,"))
        assertTrue(lines.last().startsWith("503,capture,"))
    }

    @Test(timeout = 30000) fun `writer failure leaves history intact and releases transaction`() = runBlocking {
        repository.saveMeasurement(fixtureMeasurement())
        val brokenWriter = object : Writer() {
            override fun write(chars: CharArray, offset: Int, length: Int) { throw IOException("Storage full") }
            override fun flush() = Unit
            override fun close() = Unit
        }
        try { repository.exportRawObservations(brokenWriter); fail("Expected write failure") }
        catch (e: IOException) { assertEquals("Storage full", e.message) }
        repository.saveMeasurement(fixtureMeasurement("after-failure"))
        assertEquals(2, repository.getAllMeasurements().first().size)
    }

    @Test(timeout = 30000) fun `unknown Wi-Fi numbers and unconfirmed connection remain null in storage`() = runBlocking {
        val m = fixtureMeasurement()
        repository.saveMeasurement(m.copy(wifiSources = listOf(m.wifiSources.single().copy(frequency = 0, channel = 0))))
        val wifi = repository.getObservations(m.id).first().single { it.sourceType == SourceType.WIFI }.wifi!!
        assertNull(wifi.frequencyMhz)
        assertNull(wifi.channel)
        assertNull(wifi.connected)
    }

    @Test(timeout = 30000) fun `a concurrent new capture waits and is excluded from the export snapshot`() = runBlocking {
        val m = fixtureMeasurement()
        repository.saveMeasurement(m.copy(wifiSources = (1..503).map {
            m.wifiSources.single().copy(id = "wifi-$it", bssid = "wifi-$it")
        }, bluetoothSources = emptyList(), cellularSources = emptyList()))
        val started = CompletableDeferred<Unit>()
        var insertJob: Job? = null
        val out = object : StringWriter() {
            override fun write(str: String) {
                if (insertJob == null) {
                    insertJob = launch(Dispatchers.IO) {
                        started.complete(Unit)
                        repository.saveMeasurement(fixtureMeasurement("during-export"))
                    }
                    runBlocking { withTimeout(10000) { started.await() } }
                }
                super.write(str)
            }
        }
        assertEquals(503L, repository.exportRawObservations(out))
        insertJob!!.join()
        assertFalse(out.toString().contains("during-export"))
        assertEquals(2, repository.getAllMeasurements().first().size)
    }

    @Test(timeout = 30000) fun `selected document export writes UTF-8 and closes the output`() = runBlocking {
        val m = fixtureMeasurement().let { it.copy(wifiSources = listOf(it.wifiSources.single().copy(name = "Maison, café \"☕\"\nétage"))) }
        repository.saveMeasurement(m)
        val file = java.io.File(context.cacheDir, "signals.csv")
        assertEquals(3L, RawCsvExporter(context, repository).export(Uri.fromFile(file)))
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("\"Maison, café \"\"☕\"\"\nétage\""))
        assertTrue(text.endsWith("\r\n"))
    }
}
