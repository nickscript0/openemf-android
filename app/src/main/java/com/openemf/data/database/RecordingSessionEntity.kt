package com.openemf.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class RecordingSessionStatus { RECORDING, PAUSED, STOPPED, INTERRUPTED, FAILED }

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val updatedAt: Instant,
    val status: RecordingSessionStatus,
    val reason: String?,
    val intervalMs: Long,
    val modalities: String
)

data class RecordingSessionSummary(
    @Embedded val session: RecordingSessionEntity,
    val captureCount: Long,
    val lastCaptureAt: Instant?
)

@Dao
interface RecordingSessionDao {
    @Insert suspend fun insert(session: RecordingSessionEntity)
    @Query("UPDATE recording_sessions SET status=:status, reason=:reason, endedAt=:endedAt, updatedAt=:updatedAt WHERE id=:id")
    suspend fun update(id: String, status: RecordingSessionStatus, reason: String?, endedAt: Instant?, updatedAt: Instant): Int

    @Query("""UPDATE recording_sessions SET status='INTERRUPTED', endedAt=NULL, updatedAt=:now,
        reason='Process ended without a clean stop; end time is unknown'
        WHERE status IN ('RECORDING', 'PAUSED')""")
    suspend fun interruptUnfinished(now: Instant)

    @Query("""SELECT r.*, COUNT(c.measurementId) AS captureCount, MAX(m.timestamp) AS lastCaptureAt
        FROM recording_sessions r LEFT JOIN captures c ON c.sessionId=r.id
        LEFT JOIN measurements m ON m.id=c.measurementId
        GROUP BY r.id ORDER BY r.startedAt DESC, r.id LIMIT 20""")
    fun recent(): Flow<List<RecordingSessionSummary>>

    @Query("SELECT * FROM recording_sessions WHERE id=:id")
    suspend fun get(id: String): RecordingSessionEntity?
    @Query("DELETE FROM recording_sessions") suspend fun deleteAll()
}
