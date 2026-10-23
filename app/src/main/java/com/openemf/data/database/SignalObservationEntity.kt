package com.openemf.data.database

import androidx.room.*
import com.openemf.sensors.api.*
import java.time.Instant

/** A capture is one acquisition attempt. A recording session is assigned only by a recorder. */
@Entity(tableName = "captures", foreignKeys = [ForeignKey(
    entity = MeasurementEntity::class, parentColumns = ["id"], childColumns = ["measurementId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index("sessionId")])
data class CaptureEntity(
    @PrimaryKey val measurementId: String,
    val mode: CaptureMode,
    val sessionId: String?,
    val elapsedRealtimeMs: Long?,
    val bootCount: Int?,
    val isForeground: Boolean,
    val motionState: MotionState
)

/** Saved even for denied, empty, or failed acquisitions that produce no observations. */
@Entity(tableName = "radio_acquisitions", primaryKeys = ["measurementId", "modality"],
    foreignKeys = [ForeignKey(entity = MeasurementEntity::class, parentColumns = ["id"],
        childColumns = ["measurementId"], onDelete = ForeignKey.CASCADE)])
data class RadioAcquisitionEntity(
    val measurementId: String,
    val modality: Modality,
    val status: AcquisitionStatus,
    val detail: String?
)

/** One source as returned in one capture, including repeated cache reads, never an invented sample. */
@Entity(tableName = "signal_observations", foreignKeys = [ForeignKey(
    entity = MeasurementEntity::class, parentColumns = ["id"], childColumns = ["measurementId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index("measurementId"), Index(value = ["measurementId", "sourceType", "sourceId"], unique = true),
    Index(value = ["sourceType", "sourceId"])])
data class SignalObservationEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val measurementId: String,
    val sourceId: String,
    val sourceType: SourceType,
    val name: String?,
    val signalDbm: Int?,
    val signalMetric: String,
    val observedAt: Instant?,
    val observedElapsedRealtimeMs: Long?,
    val ageAtCaptureMs: Long?,
    val freshnessAtCapture: Freshness,
    val freshForMs: Long,
    val timeBasis: ObservationTimeBasis,
    val cached: Boolean,
    @Embedded(prefix = "wifi_") val wifi: WifiObservation? = null,
    @Embedded(prefix = "bluetooth_") val bluetooth: BluetoothObservation? = null,
    @Embedded(prefix = "cellular_") val cellular: CellularObservation? = null
)

data class WifiObservation(
    val bssid: String,
    val frequencyMhz: Int?,
    val band: WifiBand,
    val channel: Int?,
    val channelWidth: WifiChannelWidth,
    val standard: WifiStandard,
    val security: WifiSecurity,
    val connected: Boolean?
)

data class BluetoothObservation(
    val address: String,
    val deviceType: BluetoothDeviceType,
    val transport: BluetoothTransport,
    // Discovery does not establish connection state. Null means unknown.
    val connected: Boolean?
)

data class CellularObservation(
    val technology: CellularTechnology,
    val subscriptionId: Int?,
    val band: Int?,
    val cellId: Long?,
    val registered: Boolean,
    @Embedded(prefix = "details_") val details: CellularDetails?
)

/** Joined provenance for a raw export row or a source's durable timeline. */
data class ObservationRecord(
    @Embedded val observation: SignalObservationEntity,
    val capturedAt: Instant,
    val captureMode: CaptureMode,
    val recordingSessionId: String?,
    val captureElapsedRealtimeMs: Long?,
    val bootCount: Int?,
    val acquisitionStatus: AcquisitionStatus,
    val acquisitionDetail: String?,
    val isForeground: Boolean,
    val motionState: MotionState
)

data class SourceHistorySummary(
    val sourceType: SourceType,
    val sourceId: String,
    val name: String?,
    val captureCount: Long,
    val lastCapturedAt: Instant
)

@Dao
interface ObservationDao {
    @Insert suspend fun insertCapture(capture: CaptureEntity)
    @Insert suspend fun insertAcquisitions(acquisitions: List<RadioAcquisitionEntity>)
    @Insert suspend fun insertObservations(observations: List<SignalObservationEntity>)

    @Query("SELECT * FROM captures WHERE measurementId = :id")
    suspend fun getCapture(id: String): CaptureEntity?

    @Query("SELECT * FROM radio_acquisitions WHERE measurementId = :id ORDER BY modality")
    suspend fun getAcquisitions(id: String): List<RadioAcquisitionEntity>

    @Query("SELECT * FROM signal_observations WHERE measurementId = :id ORDER BY sourceType, sourceId")
    fun forMeasurement(id: String): kotlinx.coroutines.flow.Flow<List<SignalObservationEntity>>

    @Query("""SELECT s.sourceType, s.sourceId,
        (SELECT name FROM signal_observations latest WHERE latest.sourceType=s.sourceType
         AND latest.sourceId=s.sourceId ORDER BY latest.rowId DESC LIMIT 1) AS name,
        COUNT(*) AS captureCount, MAX(m.timestamp) AS lastCapturedAt
        FROM signal_observations s JOIN measurements m ON m.id=s.measurementId
        GROUP BY s.sourceType, s.sourceId ORDER BY lastCapturedAt DESC, s.sourceType, s.sourceId""")
    fun sources(): kotlinx.coroutines.flow.Flow<List<SourceHistorySummary>>

    @Query(JOINED + " WHERE s.sourceType=:type AND s.sourceId=:sourceId AND (:beforeRowId IS NULL OR s.rowId < :beforeRowId) ORDER BY s.rowId DESC LIMIT :limit")
    suspend fun sourceHistory(type: SourceType, sourceId: String, limit: Int, beforeRowId: Long?): List<ObservationRecord>

    // A bounded reactive chart window, independent of the raw-history pagination cursor.
    @Query(JOINED + " WHERE s.sourceType=:type AND s.sourceId=:sourceId AND m.timestamp >= :since ORDER BY m.timestamp DESC, s.rowId DESC LIMIT :limit")
    fun chartWindow(type: SourceType, sourceId: String, since: Instant, limit: Int): kotlinx.coroutines.flow.Flow<List<ObservationRecord>>

    @Query("SELECT COALESCE(MAX(rowId), 0) FROM signal_observations")
    suspend fun maxRowId(): Long

    @Query(JOINED + " WHERE s.rowId > :after AND s.rowId <= :through ORDER BY s.rowId LIMIT :limit")
    suspend fun exportPage(after: Long, through: Long, limit: Int): List<ObservationRecord>

    companion object {
        const val JOINED = """SELECT s.*, m.timestamp AS capturedAt, c.mode AS captureMode,
            c.sessionId AS recordingSessionId, c.elapsedRealtimeMs AS captureElapsedRealtimeMs,
            c.bootCount AS bootCount, a.status AS acquisitionStatus, a.detail AS acquisitionDetail,
            c.isForeground AS isForeground, c.motionState AS motionState
            FROM signal_observations s JOIN measurements m ON m.id=s.measurementId
            JOIN captures c ON c.measurementId=s.measurementId
            JOIN radio_acquisitions a ON a.measurementId=s.measurementId AND a.modality=s.sourceType"""
    }
}
