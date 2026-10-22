package com.openemf.sensors

import com.openemf.sensors.acquisition.*
import com.openemf.sensors.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AcquisitionLifecycleTest {
    @Test(timeout = 5000) fun `synchronous callback completes and unregisters exactly once`() = runBlocking {
        var released = 0
        val result = callbackScan<Int>(1000) { complete, _ ->
            complete(42)
            val cleanup: () -> Unit = { released++ }
            cleanup
        }
        assertEquals(42, result)
        assertEquals(1, released)
    }

    @Test(timeout = 5000) fun `duplicate and late callbacks cannot complete the request twice`() = runBlocking {
        var released = 0
        lateinit var late: (Int) -> Unit
        val result = callbackScan<Int>(1000) { complete, fail ->
            late = complete
            complete(1)
            complete(2)
            fail(IllegalStateException("Late failure"))
            val cleanup: () -> Unit = { released++ }
            cleanup
        }
        late(3)
        assertEquals(1, result)
        assertEquals(1, released)
    }

    @Test(timeout = 5000) fun `missing broadcast times out and releases receiver`() = runBlocking {
        var released = 0
        lateinit var late: (Int) -> Unit
        val result = callbackScan<Int>(100) { complete, _ ->
            late = complete
            val cleanup: () -> Unit = { released++ }
            cleanup
        }
        assertNull(result)
        assertEquals(1, released)
        late(42)
        assertEquals(1, released)
    }

    @Test(timeout = 5000) fun `cancelling the caller releases active callback registration`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val released = AtomicInteger()
        val job = launch {
            callbackScan<Int>(10000) { _, _ ->
                ready.complete(Unit)
                val cleanup: () -> Unit = { released.incrementAndGet() }
                cleanup
            }
        }
        ready.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, released.get())
    }

    @Test(timeout = 5000) fun `callback security failure releases registration and reports denied permission`() = runBlocking {
        var released = 0
        val result = acquireRadio<WifiSource>(1000) {
            callbackScan<AcquisitionResult<WifiSource>>(1000) { _, fail ->
                fail(SecurityException("Permission revoked"))
                val cleanup: () -> Unit = { released++ }
                cleanup
            }!!
        }
        assertEquals(AcquisitionStatus.PERMISSION_DENIED, result.report.status)
        assertTrue(result.sources.isEmpty())
        assertEquals(1, released)
    }

    @Test(timeout = 5000) fun `failed radio does not cancel independent successful radio`() = runBlocking {
        val failed = async { acquireRadio<WifiSource>(1000) { throw IllegalStateException("Radio unavailable") } }
        val successful = async {
            acquireRadio<WifiSource>(1000) { yield(); AcquisitionResult(emptyList(), AcquisitionStatus.UPDATED) }
        }
        assertEquals(AcquisitionStatus.FAILED, failed.await().report.status)
        assertEquals(AcquisitionStatus.UPDATED, successful.await().report.status)
    }

    @Test(timeout = 5000) fun `radio deadline becomes timeout rather than successful empty detection`() = runBlocking {
        val result = acquireRadio<WifiSource>(100) { awaitCancellation() }
        assertEquals(AcquisitionStatus.TIMED_OUT, result.report.status)
        assertNotEquals(AcquisitionStatus.UPDATED, result.report.status)
    }

    @Test(timeout = 5000) fun `parent cancellation propagates through radio guard and unregisters callback`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        var released = 0
        var returned = false
        val job = launch {
            acquireRadio<WifiSource>(10000) {
                callbackScan<AcquisitionResult<WifiSource>>(10000) { _, _ ->
                    ready.complete(Unit)
                    val cleanup: () -> Unit = { released++ }
                    cleanup
                }!!
            }
            returned = true
        }
        ready.await()
        job.cancelAndJoin()
        assertEquals(1, released)
        assertFalse(returned)
    }

    private fun source(dbm: Int?, time: Long?) = WifiSource(
        "wifi:one", "One", "00:11:22:33:44:55", dbm, 2412, WifiBand.WIFI_2_4_GHZ, 1,
        observation = Observation(elapsedRealtimeMs = time))

    @Test(timeout = 5000) fun `latest weaker signal replaces earlier stronger signal and old batches cannot overwrite it`() {
        val cache = LatestObservations<WifiSource>()
        cache.add(source(-40, 1000))
        cache.add(source(-80, 2000))
        cache.add(source(-30, 1500))
        assertEquals(-80, cache.snapshot().single().rssi)
        assertEquals(2000L, cache.snapshot().single().observation.elapsedRealtimeMs)
    }

    @Test(timeout = 5000) fun `new unavailable reading replaces old numeric value without inventing strength`() {
        val cache = LatestObservations<WifiSource>()
        cache.add(source(-40, 1000))
        cache.add(source(null, 2000))
        assertNull(cache.snapshot().single().rssi)
    }

    @Test(timeout = 5000) fun `unknown timestamp cannot make a known observation appear newer`() {
        val cache = LatestObservations<WifiSource>()
        cache.add(source(-60, 1000))
        cache.add(source(-10, null))
        assertEquals(-60, cache.snapshot().single().rssi)
        assertEquals(1000L, cache.snapshot().single().observation.elapsedRealtimeMs)
    }
}
