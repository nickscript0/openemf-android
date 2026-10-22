package com.openemf.recording

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit recording only. Each interval has a timeout, plus unconditional pause/stop cleanup. */
@Singleton
class RecordingWakeLock @Inject constructor(@ApplicationContext private val context: Context) {
    private var current: PowerManager.WakeLock? = null

    @Synchronized fun acquire(timeoutMs: Long): AutoCloseable {
        release()
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenEMF:Recording")
        lock.setReferenceCounted(false)
        lock.acquire(timeoutMs.coerceIn(1000, 660_000))
        current = lock
        return AutoCloseable {
            synchronized(this) {
                if (lock.isHeld) lock.release()
                if (current === lock) current = null
            }
        }
    }

    @Synchronized fun release() {
        current?.let { if (it.isHeld) it.release() }
        current = null
    }
}
