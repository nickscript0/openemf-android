package com.openemf.recording

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** User commands only; this controller never owns an acquisition coroutine. */
@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RecordingStateStore,
    private val environment: RecordingEnvironment,
    private val notifications: RecordingNotifications
) {
    val state = store.state

    fun start(intervalMs: Long) {
        if (state.value.active) return
        if (!store.appVisible) { store.reject("Open the app to start recording"); return }
        notifications.ensureChannel()
        environment.startFailure()?.let { store.reject(it); return }
        try {
            val request = RecordingPolicy.request(UUID.randomUUID().toString(), intervalMs, environment.permissions())
            if (!store.reserve(request)) return
            try { ContextCompat.startForegroundService(context, RecordingService.command(context, request.sessionId, RecordingService.START)) }
            catch (e: Exception) { store.failStart(request.sessionId, "Could not start recording: ${e.message ?: "Android refused the service"}") }
        } catch (e: IllegalArgumentException) { store.reject("Choose a recording interval between 30 seconds and 10 minutes") }
    }

    fun pause() = send(RecordingService.PAUSE)
    fun resume() = send(RecordingService.RESUME)
    fun stop() {
        val id = state.value.sessionId
        if (id != null && store.cancelPending()) {
            context.stopService(RecordingService.command(context, id, RecordingService.STOP))
        } else send(RecordingService.STOP)
    }
    fun notificationDenied() = store.reject("Allow notifications to use the recording controls")

    private fun send(action: String) {
        val current = state.value
        if (!current.active || current.sessionId == null) return
        try { context.startService(RecordingService.command(context, current.sessionId, action)) }
        catch (e: Exception) {
            // Stop remains possible even if Android refuses command delivery.
            if (action == RecordingService.STOP) {
                store.update(current.sessionId) { it.copy(phase = RecordingPhase.STOPPING) }
                context.stopService(RecordingService.command(context, current.sessionId, action))
            } else store.update(current.sessionId) { it.copy(message = "Android refused the recording control: ${e.message ?: "service unavailable"}") }
        }
    }
}
