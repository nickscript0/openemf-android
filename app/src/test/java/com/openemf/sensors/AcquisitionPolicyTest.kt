package com.openemf.sensors

import com.openemf.sensors.api.*
import com.openemf.sensors.scoring.EScoreCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AcquisitionPolicyTest {
    private val wall = Instant.parse("2026-10-22T12:00:00Z")
    private fun wifi(dbm: Int?, observation: Observation) = WifiSource(
        "wifi:one", "One", "00:11:22:33:44:55", dbm, 5955, WifiBand.WIFI_6_GHZ, 1,
        observation = observation)

    @Test fun `LTE RSSNR normalizes legacy tenths of a dB without losing raw precision`() {
        assertEquals(12.0, lteRssnrDb(120, 29)!!, 0.001)
        assertEquals(-4.3, lteRssnrDb(-43, 26)!!, 0.001)
        assertEquals(12.0, lteRssnrDb(12, 30)!!, 0.001)
        assertNull(lteRssnrDb(Int.MAX_VALUE, 35))
    }

    @Test fun `cache rereads preserve observation time and become stale`() {
        val initial = Observation.fromBootTime(1000, 2000, wall, cached = true)
        val reread = Observation.fromBootTime(1000, 123000, wall.plusMillis(121000), cached = true)
        assertEquals(initial.timestamp, reread.timestamp)
        assertEquals(122000L, reread.ageMs(123000))
        assertTrue(reread.cached)
        assertEquals(Freshness.STALE, reread.freshness(123000))
    }

    @Test fun `missing zero and future hardware timestamps remain unknown`() {
        listOf(0L, -1L, Long.MAX_VALUE, 2001L).forEach {
            val observation = Observation.fromBootTime(it, 2000, wall)
            assertNull(observation.timestamp)
            assertNull(observation.ageMs(2000))
            assertEquals(Freshness.UNKNOWN, observation.freshness(2000))
        }
    }

    @Test fun `freshness expires at explicit radio threshold`() {
        val ble = Observation.fromBootTime(1000, 1000, wall, freshForMs = 30000)
        assertEquals(Freshness.FRESH, ble.freshness(31000))
        assertEquals(Freshness.STALE, ble.freshness(31001))
    }

    @Test fun `stale and unknown readings do not enter current relative score`() {
        val calculator = EScoreCalculator()
        val fresh = wifi(-60, Observation.fromBootTime(200000, 200000, wall))
        val stale = wifi(-10, Observation.fromBootTime(1000, 200000, wall))
        val unknown = wifi(-10, Observation())
        val score = calculator.calculateFresh(listOf(fresh, stale, unknown), 200000)
        assertEquals(1, score.sourceCount)
        assertEquals(-60, score.cumulativeDbm)
        assertEquals(0, calculator.calculateFresh(listOf(fresh), 320001).sourceCount)
    }

    @Test fun `unavailable signal remains null and contributes no power`() {
        val reading = wifi(availableInt(Int.MAX_VALUE), Observation.fromBootTime(1000, 1000, wall))
        assertNull(reading.rssi)
        assertEquals(0, EScoreCalculator().calculateFresh(listOf(reading), 1000).sourceCount)
    }

    @Test fun `full 36 bit NCI including former int sentinel is preserved`() {
        assertEquals(68719476735L, availableNci(68719476735L))
        assertEquals(2147483647L, availableNci(Int.MAX_VALUE.toLong()))
        assertNull(availableNci(Long.MAX_VALUE))
        assertNull(availableNci(68719476736L))
        assertNull(availableNci(-1))
        val cell = CellularSource("nr", null, -95, CellularTechnology.NR_5G, null,
            availableNci(68719476735L), details = CellularDetails(nci = 68719476735L))
        assertEquals(68719476735L, cell.cellId)
        assertNull(cell.details?.eci)
        assertNull(cell.details?.calculatedEnb)
    }

    @Test fun `cell identity distinguishes high NCI bits subscription and PLMN width`() {
        fun id(nci: Long, sub: Int = 1, plmn: String = "302001") =
            cellularIdentity(CellularTechnology.NR_5G, sub, plmn, 10, nci, 25, 640000)
        assertNotEquals(id(1), id(4294967297))
        assertNotEquals(id(1), id(1, sub = 2))
        assertNotEquals(id(1), id(1, plmn = "30201"))
        assertEquals(id(4294967297), id(4294967297))
    }

    @Test fun `PLMN retains leading zeros and three digit MNC`() {
        assertEquals("302-001", CellularDetails(mcc = 302, mnc = 1, plmn = "302001").formattedPlmn)
        assertEquals("302-01", CellularDetails(mcc = 302, mnc = 1, plmn = "30201").formattedPlmn)
    }

    @Test fun `known LTE and NR identity survives radio metadata changes`() {
        listOf(CellularTechnology.LTE_4G, CellularTechnology.NR_5G).forEach {
            assertEquals(cellularIdentity(it, 1, "302001", 10, 12345, 20, 640000),
                cellularIdentity(it, 1, "302001", 11, 12345, 21, 650000))
        }
        assertNotEquals(cellularIdentity(CellularTechnology.GSM_2G, 1, "302001", 10, 123, null, null),
            cellularIdentity(CellularTechnology.GSM_2G, 1, "302001", 11, 123, null, null))
    }

    @Test fun `NR SS RSRP uses Android NR range and retains missing CSI metrics`() {
        val cell = CellularSource("nr", null, -150, CellularTechnology.NR_5G, null, 4294967297,
            details = CellularDetails(ssRsrp = -150, ssRsrq = -20, ssSinr = -5))
        assertTrue(EScoreCalculator().isValidSignal(cell))
        assertTrue(EScoreCalculator().isValidSignal(cell.copy(rssi = -31)))
        assertFalse(EScoreCalculator().isValidSignal(cell.copy(rssi = null)))
        assertNull(cell.details?.csiRsrp)
    }
}
