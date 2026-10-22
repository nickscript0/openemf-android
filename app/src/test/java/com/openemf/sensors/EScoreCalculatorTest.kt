package com.openemf.sensors

import com.openemf.sensors.api.*
import com.openemf.sensors.scoring.EScoreCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for E-Score calculation.
 *
 * These tests validate the ElectroSmart algorithm implementation:
 * - Power aggregation in milliwatts
 * - Linear score formula: score = floor(2 * (dBm + 140) / 3)
 * - Level thresholds: LOW (<-75), MODERATE (-75 to -34), HIGH (>-34)
 */
class EScoreCalculatorTest {

    private lateinit var calculator: EScoreCalculator

    @Before
    fun setup() {
        calculator = EScoreCalculator()
    }

    // ==================== dBm <-> mW Conversion Tests ====================

    @Test
    fun `dBm to milliwatts - standard reference points`() {
        assertEquals(1.0, calculator.dbmToMilliwatts(0), 0.0001)        // 0 dBm = 1 mW
        assertEquals(0.001, calculator.dbmToMilliwatts(-30), 0.0001)    // -30 dBm = 1 µW
        assertEquals(0.000001, calculator.dbmToMilliwatts(-60), 1e-9)   // -60 dBm = 1 nW
        assertEquals(10.0, calculator.dbmToMilliwatts(10), 0.0001)      // 10 dBm = 10 mW
        assertEquals(0.1, calculator.dbmToMilliwatts(-10), 0.0001)      // -10 dBm = 0.1 mW
    }

    @Test
    fun `milliwatts to dBm - standard reference points`() {
        assertEquals(0, calculator.milliwattsToDbm(1.0))        // 1 mW = 0 dBm
        assertEquals(-30, calculator.milliwattsToDbm(0.001))    // 1 µW = -30 dBm
        assertEquals(-60, calculator.milliwattsToDbm(0.000001)) // 1 nW = -60 dBm
        assertEquals(10, calculator.milliwattsToDbm(10.0))      // 10 mW = 10 dBm
    }

    @Test
    fun `CRITICAL - two equal signals add 3 dB`() {
        // This is THE key physics test
        val mw1 = calculator.dbmToMilliwatts(-40)
        val mw2 = calculator.dbmToMilliwatts(-40)
        val totalMw = mw1 + mw2
        val totalDbm = calculator.milliwattsToDbm(totalMw)

        // Two -40 dBm signals = -37 dBm (double power = +3 dB)
        assertEquals("Two -40 dBm signals should equal -37 dBm", -37, totalDbm)
    }

    @Test
    fun `ten equal signals add 10 dB`() {
        val singleMw = calculator.dbmToMilliwatts(-50)
        val totalMw = singleMw * 10
        val totalDbm = calculator.milliwattsToDbm(totalMw)

        // 10x power = +10 dB
        assertEquals("Ten -50 dBm signals should equal -40 dBm", -40, totalDbm)
    }

    // ==================== Score Formula Tests ====================

    @Test
    fun `score at noise floor (-140 dBm) is 0`() {
        val sources = listOf(createWifiSource(-140))
        val result = calculator.calculate(sources)
        assertEquals(0, result.score)
    }

    @Test
    fun `score at -100 dBm is approximately 27`() {
        // score = floor(2 * (-100 + 140) / 3) = floor(80/3) = 26
        val sources = listOf(createWifiSource(-100))
        val result = calculator.calculate(sources)
        assertTrue("Score should be 26-27, was ${result.score}", result.score in 25..28)
    }

    @Test
    fun `score at LOW threshold (-75 dBm) is approximately 43`() {
        // score = floor(2 * (-75 + 140) / 3) = floor(130/3) = 43
        val sources = listOf(createWifiSource(-75))
        val result = calculator.calculate(sources)
        assertEquals(43, result.score)
    }

    @Test
    fun `score at HIGH threshold (-34 dBm) is approximately 70-71`() {
        // score = floor(2 * (-34 + 140) / 3) = floor(212/3) = 70
        val sources = listOf(createWifiSource(-34))
        val result = calculator.calculate(sources)
        assertTrue("Score should be 70-71, was ${result.score}", result.score in 69..72)
    }

    @Test
    fun `score at 0 dBm is approximately 93`() {
        // score = floor(2 * (0 + 140) / 3) = floor(280/3) = 93
        // Ten valid -10 dBm sources sum to 0 dBm. A single 0 dBm WiFi
        // reading is outside the scanner's valid range and is filtered out.
        val sources = List(10) { createWifiSource(-10, index = it) }
        val result = calculator.calculate(sources)
        assertEquals(10, result.sourceCount)
        assertEquals(0, result.cumulativeDbm)
        assertTrue("Score should be ~93, was ${result.score}", result.score in 92..95)
    }

    @Test
    fun `score at +10 dBm is 100`() {
        // One hundred valid -10 dBm sources sum to +10 dBm.
        val sources = List(100) { createWifiSource(-10, index = it) }
        val result = calculator.calculate(sources)
        assertEquals(100, result.sourceCount)
        assertEquals(10, result.cumulativeDbm)
        assertEquals(100, result.score)
    }

    // ==================== Level Classification Tests ====================

    @Test
    fun `level is LOW below -75 dBm`() {
        val sources = listOf(createWifiSource(-80))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.LOW, result.level)
    }

    @Test
    fun `level is LOW at exactly -76 dBm`() {
        val sources = listOf(createWifiSource(-76))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.LOW, result.level)
    }

    @Test
    fun `level is MODERATE at exactly -75 dBm`() {
        val sources = listOf(createWifiSource(-75))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.MODERATE, result.level)
    }

    @Test
    fun `level is MODERATE between -75 and -34 dBm`() {
        val sources = listOf(createWifiSource(-50))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.MODERATE, result.level)
    }

    @Test
    fun `level is MODERATE at exactly -35 dBm`() {
        val sources = listOf(createWifiSource(-35))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.MODERATE, result.level)
    }

    @Test
    fun `level is HIGH at exactly -34 dBm`() {
        val sources = listOf(createWifiSource(-34))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.HIGH, result.level)
    }

    @Test
    fun `level is HIGH above -34 dBm`() {
        val sources = listOf(createWifiSource(-30))
        val result = calculator.calculate(sources)
        assertEquals(ExposureLevel.HIGH, result.level)
    }

    // ==================== Power Aggregation Tests ====================

    @Test
    fun `multiple weak signals aggregate correctly`() {
        // 10 signals at -70 dBm each = -60 dBm total
        val sources = (1..10).map { createWifiSource(-70) }
        val result = calculator.calculate(sources)

        // -60 dBm: score = floor(2 * 80 / 3) = 53
        assertTrue("Score should be ~53, was ${result.score}", result.score in 52..55)
    }

    @Test
    fun `one strong signal dominates many weak ones`() {
        // -30 dBm = 0.001 mW, -70 dBm = 0.0000001 mW
        // Strong signal is 10,000x more powerful
        val sources = listOf(
            createWifiSource(-30),  // Dominant
            createWifiSource(-70),
            createWifiSource(-70),
            createWifiSource(-70),
            createWifiSource(-70)
        )
        val result = calculator.calculate(sources)

        // Total ≈ -30 dBm, score ≈ 73
        assertTrue("Strong signal should dominate, score ~73, was ${result.score}",
            result.score in 70..76)
    }

    // ==================== Breakdown Tests ====================

    @Test
    fun `breakdown percentages sum to approximately 100`() {
        val sources = listOf(
            createWifiSource(-40),
            createCellularSource(-50),
            createBluetoothSource(-60),
        )
        val result = calculator.calculate(sources)

        val sum = result.breakdown.values.sum()
        assertTrue("Breakdown should sum to ~100, was $sum", sum in 98..102)
    }

    @Test
    fun `equal power sources have equal breakdown`() {
        val sources = listOf(
            createWifiSource(-50),
            createCellularSource(-50), // Valid LTE range ends at -43 dBm.
        )
        val result = calculator.calculate(sources)

        assertEquals(2, result.sourceCount)
        assertEquals(50, result.breakdown[SourceType.WIFI])
        assertEquals(50, result.breakdown[SourceType.CELLULAR])
    }

    @Test
    fun `breakdown reflects power ratio not count ratio`() {
        // WiFi at -30 dBm = 0.001 mW
        // 3x Cellular at -60 dBm = 3 * 0.000001 mW = 0.000003 mW
        // WiFi is ~99.7% of total power
        val sources = listOf(
            createWifiSource(-30),
            createCellularSource(-60),
            createCellularSource(-60),
            createCellularSource(-60),
        )
        val result = calculator.calculate(sources)

        assertTrue("WiFi should be >99%, was ${result.breakdown[SourceType.WIFI]}",
            result.breakdown[SourceType.WIFI]!! >= 99)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `empty source list returns score 0 and LOW level`() {
        val result = calculator.calculate(emptyList())
        assertEquals(0, result.score)
        assertEquals(ExposureLevel.LOW, result.level)
        assertEquals(0, result.sourceCount)
    }

    @Test
    fun `invalid signals are filtered out`() {
        val sources = listOf(
            createWifiSource(-200),  // Invalid - below range
            createWifiSource(-50),   // Valid
        )
        val result = calculator.calculate(sources)
        assertEquals(1, result.sourceCount)
    }

    @Test
    fun `dominant source is identified correctly`() {
        val sources = listOf(
            createWifiSource(-70, "WeakNetwork"),
            createWifiSource(-30, "StrongNetwork"),
        )
        val result = calculator.calculate(sources)
        assertEquals("StrongNetwork", result.dominantSource?.name)
    }

    // ==================== Real-World Scenario Tests ====================

    @Test
    fun `typical home environment scores LOW to MODERATE`() {
        val sources = listOf(
            createWifiSource(-55),   // Own router
            createWifiSource(-65),   // Neighbor 1
            createWifiSource(-72),   // Neighbor 2
            createBluetoothSource(-70),
            createBluetoothSource(-80),
            createCellularSource(-95),
            createCellularSource(-105),
        )
        val result = calculator.calculate(sources)

        assertTrue("Home should be LOW or MODERATE",
            result.level in listOf(ExposureLevel.LOW, ExposureLevel.MODERATE))
        assertTrue("Home score should be 35-60, was ${result.score}",
            result.score in 35..60)
    }

    @Test
    fun `next to router scores HIGH`() {
        val sources = listOf(
            createWifiSource(-25),  // Very close to router
        )
        val result = calculator.calculate(sources)

        assertEquals(ExposureLevel.HIGH, result.level)
        assertTrue("Next to router should score 75+, was ${result.score}",
            result.score >= 75)
    }

    @Test
    fun `rural area with distant towers scores LOW`() {
        val sources = listOf(
            createCellularSource(-110),
            createCellularSource(-115),
        )
        val result = calculator.calculate(sources)

        assertEquals(ExposureLevel.LOW, result.level)
        assertTrue("Rural should score <30, was ${result.score}", result.score < 30)
    }

    // ==================== UI Helper Tests ====================

    @Test
    fun `getScoreColor returns green for LOW scores`() {
        assertEquals(0xFF22C55E, calculator.getScoreColor(20))
        assertEquals(0xFF22C55E, calculator.getScoreColor(42))
    }

    @Test
    fun `getScoreColor returns orange for MODERATE scores`() {
        assertEquals(0xFFF59E0B, calculator.getScoreColor(43))
        assertEquals(0xFFF59E0B, calculator.getScoreColor(60))
        assertEquals(0xFFF59E0B, calculator.getScoreColor(70))
    }

    @Test
    fun `getScoreColor returns red for HIGH scores`() {
        assertEquals(0xFFEF4444, calculator.getScoreColor(71))
        assertEquals(0xFFEF4444, calculator.getScoreColor(85))
        assertEquals(0xFFEF4444, calculator.getScoreColor(100))
    }

    @Test
    fun `getScoreLabel returns correct labels`() {
        assertEquals("Low", calculator.getScoreLabel(20))
        assertEquals("Moderate", calculator.getScoreLabel(50))
        assertEquals("High", calculator.getScoreLabel(80))
    }

    // ==================== Helper Functions ====================

    private fun createWifiSource(rssi: Int, name: String = "TestNetwork", index: Int = 0): WifiSource {
        return WifiSource(
            id = "wifi_${rssi}_$index",
            name = name,
            bssid = "00:11:22:33:44:${index.toString(16).padStart(2, '0')}",
            rssi = rssi,
            frequency = 2437,
            band = WifiBand.WIFI_2_4_GHZ,
            channel = 6,
            contributionPercent = 0
        )
    }

    private fun createBluetoothSource(rssi: Int): BluetoothSource {
        return BluetoothSource(
            id = "bt_$rssi",
            name = "TestDevice",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = rssi,
            deviceType = BluetoothDeviceType.UNKNOWN,
            contributionPercent = 0
        )
    }

    private fun createCellularSource(rssi: Int): CellularSource {
        return CellularSource(
            id = "cell_$rssi",
            name = "Carrier",
            rssi = rssi,
            technology = CellularTechnology.LTE_4G,
            band = null,
            cellId = null,
            contributionPercent = 0
        )
    }
}
