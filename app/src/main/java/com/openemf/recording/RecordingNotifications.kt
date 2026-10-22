package com.openemf.recording

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openemf.MainActivity
import com.openemf.app.R
import com.openemf.sensors.api.Modality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Signal recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Recording status and pause, resume, and stop controls"
            setShowBadge(false)
        })
    }

    fun allowed(): Boolean {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun build(state: RecordingState): Notification {
        val sessionId = requireNotNull(state.sessionId)
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), FLAGS)
        val paused = state.phase == RecordingPhase.PAUSED
        val text = when (state.phase) {
            RecordingPhase.STARTING -> "Preparing recording"
            RecordingPhase.PAUSING -> "Pausing acquisition"
            RecordingPhase.STOPPING -> "Stopping recording"
            else -> "${state.savedCaptures} completed captures · target ${state.intervalMs / 1000}s"
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(if (paused) "Signal recording paused" else "Signal recording")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${state.modalities.joinToString { it.name }}. Android can limit scans; cached and unavailable results remain labeled."))
            .setContentIntent(openApp)
            .setDeleteIntent(command(sessionId, RecordingService.STOP))
            .setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (state.phase == RecordingPhase.RECORDING || paused) {
                    val action = if (paused) RecordingService.RESUME else RecordingService.PAUSE
                    addAction(0, if (paused) "Resume" else "Pause", command(sessionId, action))
                }
                addAction(0, "Stop", command(sessionId, RecordingService.STOP))
            }.build()
    }

    private fun command(id: String, action: String): PendingIntent = PendingIntent.getService(context, 0,
        RecordingService.command(context, id, action), FLAGS)

    companion object {
        const val CHANNEL = "signal_recording"
        const val ID = 1001
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun serviceTypes(modalities: Set<Modality>): Int {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Modality.BLUETOOTH in modalities) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            return types
        }
    }
}
