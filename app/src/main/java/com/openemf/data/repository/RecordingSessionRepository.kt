package com.openemf.data.repository

import com.openemf.data.database.*
import com.openemf.recording.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingSessionRepository @Inject constructor(private val database: OpenEMFDatabase) : RecordingJournal {
    private val recoveryMutex = Mutex()
    private var recovered = false

    /** Once per process; never called again merely because an activity is recreated. */
    private suspend fun recover() = recoveryMutex.withLock {
        if (!recovered) {
            database.recordingSessionDao().interruptUnfinished(Instant.now())
            recovered = true
        }
    }

    val recent = flow { recover(); emitAll(database.recordingSessionDao().recent()) }

    override suspend fun begin(request: RecordingRequest) {
        recover()
        val now = Instant.now()
        database.withTransaction {
            // Also reconcile a previous session whose final journal write failed in this process.
            database.recordingSessionDao().interruptUnfinished(now)
            database.recordingSessionDao().insert(RecordingSessionEntity(request.sessionId, now, null, now,
                RecordingSessionStatus.RECORDING, null, request.intervalMs,
                request.modalities.sortedBy { it.name }.joinToString(",") { it.name }))
        }
    }

    override suspend fun paused(id: String, paused: Boolean) {
        check(database.recordingSessionDao().update(id,
            if (paused) RecordingSessionStatus.PAUSED else RecordingSessionStatus.RECORDING,
            null, null, Instant.now()) == 1) { "Recording session journal is missing" }
    }

    override suspend fun finish(id: String, status: RecordingSessionStatus, reason: String) {
        val now = Instant.now()
        check(database.recordingSessionDao().update(id, status, reason,
            if (status == RecordingSessionStatus.INTERRUPTED) null else now, now) == 1) { "Recording session journal is missing" }
    }
}
