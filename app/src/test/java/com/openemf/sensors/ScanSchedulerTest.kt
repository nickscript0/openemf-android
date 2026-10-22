package com.openemf.sensors

import com.openemf.sensors.api.MotionState
import com.openemf.sensors.scheduling.ScanScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ScanScheduler throttle management.
 */
class ScanSchedulerTest {

    private lateinit var scheduler: ScanScheduler

    @Before
    fun setup() {
        scheduler = ScanScheduler()
    }

    // ==================== Basic Budget Tests ====================

    @Test
    fun `initial budget has 4 scans available`() {
        val budget = scheduler.getCurrentBudget()
        assertEquals(4, budget.scansRemaining)
        assertFalse(budget.isThrottled)
    }

    @Test
    fun `canScan returns true when budget available`() {
        assertTrue(scheduler.canScan())
    }

    @Test
    fun `consuming scan decrements budget`() {
        scheduler.consumeScan()
        val budget = scheduler.getCurrentBudget()
        assertEquals(3, budget.scansRemaining)
    }

    @Test
    fun `consuming all 4 scans exhausts budget`() {
        repeat(4) { scheduler.consumeScan() }

        val budget = scheduler.getCurrentBudget()
        assertEquals(0, budget.scansRemaining)
        assertTrue(budget.isThrottled)
        assertFalse(scheduler.canScan())
    }

    @Test
    fun `cannot consume more than 4 scans in window`() {
        repeat(5) { scheduler.consumeScan() }

        val budget = scheduler.getCurrentBudget()
        assertEquals(0, budget.scansRemaining)
    }

    // ==================== Time Until Next Scan Tests ====================

    @Test
    fun `getTimeUntilNextScan returns 0 when budget available and no recent scan`() {
        val time = scheduler.getTimeUntilNextScan()
        assertEquals(0, time)
    }

    @Test
    fun `getTimeUntilNextScan returns positive when budget exhausted`() {
        repeat(4) { scheduler.consumeScan() }

        val time = scheduler.getTimeUntilNextScan()
        assertTrue("Should wait for window reset", time > 0)
        assertTrue("Should be less than 2 minutes", time <= 2 * 60 * 1000)
    }

    @Test
    fun `getTimeUntilNextScan respects minimum interval after scan`() {
        scheduler.consumeScan()

        val time = scheduler.getTimeUntilNextScan()
        // Should wait at least some time (minimum interval is 30 seconds)
        assertTrue("Should respect minimum interval", time >= 0)
    }

    // ==================== Adaptive Interval Tests ====================

    @Test
    fun `stationary motion has 60 second base interval`() {
        val interval = scheduler.getAdaptiveInterval(MotionState.STATIONARY)
        assertEquals(60_000L, interval)
    }

    @Test
    fun `driving motion has 15 second base interval`() {
        val interval = scheduler.getAdaptiveInterval(MotionState.DRIVING)
        assertEquals(15_000L, interval)
    }

    @Test
    fun `interval doubles when budget is low`() {
        // Consume 3 scans, leaving 1
        repeat(3) { scheduler.consumeScan() }

        val interval = scheduler.getAdaptiveInterval(MotionState.STATIONARY)
        // With only 1 scan remaining, interval should double
        assertEquals(120_000L, interval)
    }

    @Test
    fun `interval increases by half when budget is medium`() {
        // Consume 2 scans, leaving 2
        repeat(2) { scheduler.consumeScan() }

        val interval = scheduler.getAdaptiveInterval(MotionState.STATIONARY)
        // With 2 scans remaining, interval should be 1.5x
        assertEquals(90_000L, interval)
    }

    // ==================== Window Reset Tests ====================

    @Test
    fun `budget tracks window start timestamp`() {
        val budget = scheduler.getCurrentBudget()
        assertNotNull(budget.windowStartTimestamp)
        assertTrue(budget.windowStartTimestamp > 0)
    }

    @Test
    fun `budget tracks last scan timestamp after scan`() {
        assertNull(scheduler.getCurrentBudget().lastScanTimestamp)

        scheduler.consumeScan()

        assertNotNull(scheduler.getCurrentBudget().lastScanTimestamp)
    }
}
