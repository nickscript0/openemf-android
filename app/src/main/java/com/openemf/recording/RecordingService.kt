package com.openemf.recording

import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import com.openemf.data.repository.RecordingSessionRepository
import com.openemf.sensors.api.EMFSensorModule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var store: RecordingStateStore
    @Inject lateinit var environment: RecordingEnvironment
    @Inject lateinit var notifications: RecordingNotifications
    @Inject lateinit var wakeLock: RecordingWakeLock
    @Inject lateinit var sensors: EMFSensorModule
    @Inject lateinit var journal: RecordingSessionRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private var runner: RecordingRunner? = null
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
        scope.launch {
            store.state.collect { state ->
                if (promoted && state.active) {
                    if (ContextCompat.checkSelfPermission(this@RecordingService,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        runner?.stop("Recording notification permission was revoked"); work?.cancel(); return@collect
                    }
                    try { NotificationManagerCompat.from(this@RecordingService).notify(RecordingNotifications.ID, notifications.build(state)) }
                    catch (_: SecurityException) { runner?.stop("Recording notification was disabled"); work?.cancel() }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(SESSION_ID)
        if (intent?.action == START) {
            if (runner != null) return START_NOT_STICKY
            val request = store.consumeStart(id)
            if (request == null) {
                if (id != null) store.failStart(id, "Open the app and explicitly start recording again")
                stopSelf()
                return START_NOT_STICKY
            }
            val failure = environment.failure(request)
            if (failure != null) {
                store.failStart(request.sessionId, failure); stopSelf(); return START_NOT_STICKY
            }
            try {
                ServiceCompat.startForeground(this, RecordingNotifications.ID, notifications.build(store.state.value),
                    RecordingNotifications.serviceTypes(request.modalities))
                promoted = true
            } catch (e: Exception) {
                store.failStart(request.sessionId, "Android refused recording: ${e.message ?: "foreground service unavailable"}")
                stopSelf(); return START_NOT_STICKY
            }
            sensors.stopMonitoring()
            val sessionRunner = RecordingRunner(request, store, journal,
                { environment.failure(request) }, { sensors.record(request.sessionId, request.modalities) }, wakeLock::acquire)
            runner = sessionRunner
            work = scope.launch {
                try { sessionRunner.run() }
                finally {
                    wakeLock.release()
                    runner = null
                    if (promoted) { stopForeground(STOP_FOREGROUND_REMOVE); promoted = false }
                    stopSelf()
                }
            }
        } else {
            // Notification actions are session-scoped; stale actions cannot control a new session.
            val active = runner
            if (active == null) { stopSelf(); return START_NOT_STICKY }
            if (id != active.request.sessionId) return START_NOT_STICKY
            when (intent.action) {
                PAUSE -> active.pause()
                RESUME -> active.resume()
                STOP -> { active.stop(); work?.cancel() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Also runs for stopService(); an OS process kill may skip this callback entirely.
        if (store.state.value.phase == RecordingPhase.STOPPING) runner?.stop()
        else runner?.let { active -> store.update(active.request.sessionId) { state ->
            if (state.active) state.copy(phase = RecordingPhase.STOPPING, message = "Recording service ended") else state
        } }
        wakeLock.release()
        scope.cancel()
        if (promoted) { stopForeground(STOP_FOREGROUND_REMOVE); promoted = false }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val START = "com.openemf.recording.START"
        const val PAUSE = "com.openemf.recording.PAUSE"
        const val RESUME = "com.openemf.recording.RESUME"
        const val STOP = "com.openemf.recording.STOP"
        const val SESSION_ID = "recording_session_id"
        fun command(context: Context, id: String, action: String): Intent = Intent(context, RecordingService::class.java)
            .setAction(action).setData("openemf://recording/$id/${action.substringAfterLast('.')}".toUri())
            .putExtra(SESSION_ID, id)
    }
}
