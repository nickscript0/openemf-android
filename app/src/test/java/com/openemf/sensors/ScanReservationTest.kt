package com.openemf.sensors

import com.openemf.sensors.scheduling.ScanScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ScanReservationTest {
    @Test(timeout = 5000) fun `reservation enforces thirty seconds between active scans`() {
        var now = 1000L
        val scheduler = ScanScheduler { now }
        assertTrue(scheduler.tryAcquireScan())
        assertFalse(scheduler.tryAcquireScan())
        now += 29999
        assertFalse(scheduler.tryAcquireScan())
        now++
        assertTrue(scheduler.tryAcquireScan())
        assertEquals(2, scheduler.getCurrentBudget().scansRemaining)
    }

    @Test(timeout = 5000) fun `rolling expiry releases only expired attempts rather than resetting all four`() {
        var now = 1000L
        val scheduler = ScanScheduler { now }
        scheduler.consumeScan()
        listOf(101000L, 106000L, 111000L).forEach { now = it; scheduler.consumeScan() }
        assertEquals(0, scheduler.getCurrentBudget().scansRemaining)
        now = 121000
        assertEquals(1, scheduler.getCurrentBudget().scansRemaining)
        assertFalse(scheduler.tryAcquireScan()) // Last attempt was only ten seconds ago.
        now = 141000
        assertTrue(scheduler.tryAcquireScan())
        assertEquals(0, scheduler.getCurrentBudget().scansRemaining)
    }

    @Test(timeout = 5000) fun `background policy waits thirty minutes after last active attempt`() {
        var now = 1000L
        val scheduler = ScanScheduler { now }
        assertTrue(scheduler.tryAcquireScan(false))
        now += 120000
        assertFalse(scheduler.tryAcquireScan(false))
        assertEquals(1680000L, scheduler.getTimeUntilNextScan(false))
        now = 1801000
        assertTrue(scheduler.tryAcquireScan(false))
    }

    @Test(timeout = 5000) fun `concurrent requests cannot spend one scan opportunity repeatedly`() {
        val scheduler = ScanScheduler { 1000L }
        val pool = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(1)
        try {
            val reservations = List(32) { pool.submit<Boolean> { ready.await(); scheduler.tryAcquireScan() } }
            ready.countDown()
            assertEquals(1, reservations.count { it.get() })
            assertEquals(3, scheduler.getCurrentBudget().scansRemaining)
        } finally { pool.shutdownNow() }
    }
}
