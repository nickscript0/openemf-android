package com.openemf.sensors

import com.openemf.sensors.api.*
import org.junit.Assert.*
import org.junit.Test

class PermissionPolicyTest {
    @Test fun `phone denial degrades cellular without blocking other radios`() {
        listOf(31, 35, 37).forEach { api ->
            val result = PermissionPolicy.evaluate(api, PermissionPolicy.required(api) - Permission.READ_PHONE_STATE)
            assertTrue(result.canMeasure)
            assertEquals(setOf(Modality.CELLULAR), result.degradedModalities)
            assertTrue(Permission.READ_PHONE_STATE in result.denied)
        }
    }

    @Test fun `precise location denial allows independently granted modern Bluetooth`() {
        val result = PermissionPolicy.evaluate(35, setOf(Permission.BLUETOOTH_SCAN, Permission.BLUETOOTH_CONNECT))
        assertTrue(result.canMeasure)
        assertEquals(setOf(Modality.WIFI, Modality.CELLULAR), result.degradedModalities)
    }

    @Test fun `coarse location alone cannot authorize modern WiFi or cell information`() {
        listOf(29, 30, 31, 35, 37).forEach {
            val result = PermissionPolicy.evaluate(it, setOf(Permission.COARSE_LOCATION, Permission.READ_PHONE_STATE))
            assertFalse(result.canMeasure)
            assertEquals(Modality.entries.toSet(), result.degradedModalities)
        }
    }

    @Test fun `legacy coarse permission is accepted for WiFi and cellular`() {
        val result = PermissionPolicy.evaluate(26, setOf(Permission.COARSE_LOCATION, Permission.READ_PHONE_STATE))
        assertTrue(result.canMeasure)
        assertEquals(setOf(Modality.BLUETOOTH), result.degradedModalities)
    }

    @Test fun `revoked connect permission disables Bluetooth and no permissions disable acquisition`() {
        val result = PermissionPolicy.evaluate(35, PermissionPolicy.required(35) - Permission.BLUETOOTH_CONNECT)
        assertEquals(setOf(Modality.BLUETOOTH), result.degradedModalities)
        assertFalse(PermissionPolicy.evaluate(35, emptySet()).canMeasure)
    }

    @Test fun `nearby WiFi permission is not a prerequisite for scan APIs`() {
        assertFalse(Permission.NEARBY_WIFI_DEVICES in PermissionPolicy.required(35))
        val result = PermissionPolicy.evaluate(35, PermissionPolicy.required(35))
        assertTrue(result.canMeasure)
        assertTrue(result.degradedModalities.isEmpty())
        assertTrue(result.denied.isEmpty())
    }
}
