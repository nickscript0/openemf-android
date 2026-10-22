package com.openemf.sensors.scheduling

import com.openemf.sensors.api.MotionState
import com.openemf.sensors.api.ScanBudget
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/** Rolling budget and atomic reservations use a monotonic clock, independent of wall time. */
@Singleton
class ScanScheduler internal constructor(private val clock: () -> Long) {
    @Inject constructor() : this({ System.nanoTime() / 1_000_000 })

    private val attempts = ArrayDeque<Long>()
    private var lastScanTime: Long? = null
    private var windowStart = clock()

    companion object {
        const val MAX_SCANS = 4
        const val WINDOW_MS = 120_000L
        const val MIN_SCAN_INTERVAL_MS = 30_000L
        const val BACKGROUND_INTERVAL_MS = 1_800_000L
        val MOTION_INTERVALS = mapOf(
            MotionState.STATIONARY to 60_000L, MotionState.WALKING to 30_000L,
            MotionState.RUNNING to 30_000L, MotionState.CYCLING to 20_000L,
            MotionState.DRIVING to 15_000L, MotionState.UNKNOWN to 45_000L)
    }

    /** Remaining rolling-window capacity. Reservations also enforce the minimum interval. */
    @Synchronized fun canScan(): Boolean { refresh(); return attempts.size < MAX_SCANS }

    @Synchronized fun consumeScan() {
        refresh()
        if (attempts.size < MAX_SCANS) {
            val now = clock()
            if (attempts.isEmpty()) windowStart = now
            attempts.addLast(now)
            lastScanTime = now
        }
    }

    /** The only production entry point for starting an active Wi-Fi scan. */
    @Synchronized fun tryAcquireScan(isForeground: Boolean = true): Boolean {
        if (getTimeUntilNextScan(isForeground) > 0) return false
        consumeScan()
        return true
    }

    @Synchronized fun getCurrentBudget(): ScanBudget {
        refresh()
        val remaining = (attempts.firstOrNull()?.plus(WINDOW_MS)?.minus(clock()) ?: 0).coerceAtLeast(0)
        return ScanBudget(MAX_SCANS - attempts.size, ((remaining + 999) / 1000).toInt(),
            attempts.size >= MAX_SCANS, lastScanTime, windowStart)
    }

    @Synchronized fun getTimeUntilNextScan(isForeground: Boolean = true): Long {
        refresh()
        val now = clock()
        val windowWait = if (attempts.size >= MAX_SCANS) attempts.first + WINDOW_MS - now else 0
        val interval = if (isForeground) MIN_SCAN_INTERVAL_MS else BACKGROUND_INTERVAL_MS
        val intervalWait = lastScanTime?.let { it + interval - now } ?: 0
        return maxOf(0, windowWait, intervalWait)
    }

    fun getAdaptiveInterval(motionState: MotionState): Long {
        val base = MOTION_INTERVALS[motionState] ?: 45_000L
        return when (getCurrentBudget().scansRemaining) {
            0, 1 -> base * 2
            2 -> (base * 1.5).toLong()
            else -> base
        }
    }

    private fun refresh() {
        val now = clock()
        while (attempts.isNotEmpty() && now - attempts.first >= WINDOW_MS) attempts.removeFirst()
        windowStart = attempts.firstOrNull() ?: now
    }
}
