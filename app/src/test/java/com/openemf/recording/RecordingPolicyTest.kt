package com.openemf.recording

import com.openemf.sensors.api.*
import com.openemf.sensors.bluetooth.discoveryReport
import org.junit.Assert.*
import org.junit.Test

class RecordingPolicyTest {
    private val all = PermissionPolicy.required(37)

    @Test fun `recording requires precise scan access enabled location and visible notification`() {
        assertNotNull(RecordingPolicy.failure(emptySet(), true, true))
        assertNotNull(RecordingPolicy.failure(setOf(Permission.COARSE_LOCATION), true, true))
        assertNotNull(RecordingPolicy.failure(all, false, true))
        assertNotNull(RecordingPolicy.failure(all, true, false))
        assertNull(RecordingPolicy.failure(all, true, true))
    }

    @Test fun `Bluetooth denial leaves WiFi and cellular recording available`() {
        val locationOnly = setOf(Permission.FINE_LOCATION, Permission.COARSE_LOCATION)
        val request = RecordingPolicy.request("one", 60000, PermissionPolicy.evaluate(37, locationOnly))
        assertEquals(setOf(Modality.WIFI, Modality.CELLULAR), request.modalities)
        assertEquals(setOf(Permission.FINE_LOCATION), request.requiredPermissions)
        val allRequest = RecordingPolicy.request("two", 60000, PermissionPolicy.evaluate(37, all))
        assertEquals(Modality.entries.toSet(), allRequest.modalities)
        assertTrue(allRequest.requiredPermissions.containsAll(setOf(Permission.BLUETOOTH_SCAN, Permission.BLUETOOTH_CONNECT)))
    }

    @Test fun `invalid intervals are rejected instead of silently increasing scan frequency`() {
        for (interval in listOf(-1L, 0L, 29999L, 600001L)) {
            try { RecordingPolicy.request("one", interval, PermissionPolicy.evaluate(37, all)); fail("Expected interval rejection") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `starts require visibility and duplicate or replayed starts cannot replace a session`() {
        val store = RecordingStateStore()
        val request = RecordingPolicy.request("one", 60000, PermissionPolicy.evaluate(37, all))
        assertFalse(store.reserve(request))
        store.setAppVisible(true)
        assertTrue(store.reserve(request))
        assertFalse(store.reserve(request.copy(sessionId = "two")))
        assertNull(store.consumeStart("other"))
        assertEquals(request, store.consumeStart("one"))
        assertNull(store.consumeStart("one"))
        assertFalse(store.canAcquire("one")) // A pending start is not an active service recording.
        store.update("one") { it.copy(phase = RecordingPhase.RECORDING) }
        assertTrue(store.canAcquire("one"))
        assertFalse(store.canAcquire("other"))
        store.update("one") { it.copy(phase = RecordingPhase.PAUSED) }
        assertFalse(store.canAcquire("one"))
    }

    @Test fun `leaving the UI before promotion and cancellation of a pending start prevent acquisition`() {
        val store = RecordingStateStore()
        val request = RecordingPolicy.request("one", 60000, PermissionPolicy.evaluate(37, all))
        store.setAppVisible(true); assertTrue(store.reserve(request)); store.setAppVisible(false)
        assertNull(store.consumeStart("one"))
        assertTrue(store.cancelPending())
        store.setAppVisible(true)
        assertNull(store.consumeStart("one"))
        store.failStart("one", "Old start arrived late")
        assertEquals(RecordingPhase.STOPPED, store.state.value.phase)
        assertFalse(RecordingStateStore().state.value.active) // New process never restores an active flag.
    }

    @Test fun `screen-off discovery is partial even when both discovery APIs accepted the request`() {
        assertEquals(AcquisitionStatus.PARTIAL, discoveryReport(true, true, true).status)
        assertTrue(discoveryReport(true, true, true).detail!!.contains("screen is off"))
        assertEquals(AcquisitionStatus.UPDATED, discoveryReport(true, true, false).status)
        assertEquals(AcquisitionStatus.PARTIAL, discoveryReport(false, true, false).status)
        assertEquals(AcquisitionStatus.PARTIAL, discoveryReport(true, false, false).status)
    }
}
