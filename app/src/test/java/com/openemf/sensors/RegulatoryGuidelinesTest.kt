package com.openemf.sensors

import com.openemf.sensors.api.*
import com.openemf.sensors.guidelines.RegulatoryGuidelines
import com.openemf.sensors.scoring.EScoreCalculator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Tests for regulatory guidelines comparison.
 *
 * Validates exposure calculations against international standards:
 * - ICNIRP 2020 (International)
 * - FCC OET-65 (USA)
 * - Swiss OMEN (Switzerland)
 * - Brussels Regional (Belgium)
 * - SanPiN (Russia)
 *
 * Reference: https://www.icnirp.org/en/frequencies/radiofrequency/
 */
class RegulatoryGuidelinesTest {

    private lateinit var guidelines: RegulatoryGuidelines

    @Before
    fun setup() {
        guidelines = RegulatoryGuidelines()
    }

    // ========== Country Data Tests ==========

    @Test
    fun `all expected countries are available`() {
        val expectedCountries = listOf("ICNIRP", "CH", "BE", "IN", "RU", "US", "IT", "CN")

        expectedCountries.forEach { code ->
            val limits = guidelines.getLimits(code)
            assertNotNull("Country $code should be available", limits)
        }
    }

    @Test
    fun `ICNIRP limits are correct`() {
        val icnirp = guidelines.getLimits("ICNIRP")!!

        // ICNIRP 2020 RF limits
        assertEquals(10.0, icnirp.rfLimits["wifi_2.4"]!!, 0.1)
        assertEquals(10.0, icnirp.rfLimits["wifi_5"]!!, 0.1)

        // ICNIRP 2020 ELF limit
        assertEquals(200.0, icnirp.elfMagnetic, 0.1)
    }

    @Test
    fun `Switzerland limits are stricter than ICNIRP`() {
        val icnirp = guidelines.getLimits("ICNIRP")!!
        val switzerland = guidelines.getLimits("CH")!!

        // Swiss installation limits are ~100x stricter
        assertTrue(
            "Swiss RF limit should be stricter",
            switzerland.rfLimits["wifi_2.4"]!! < icnirp.rfLimits["wifi_2.4"]!!
        )
        assertTrue(
            "Swiss ELF limit should be stricter",
            switzerland.elfMagnetic < icnirp.elfMagnetic
        )
    }

    @Test
    fun `Brussels has strictest RF limits`() {
        val brussels = guidelines.getLimits("BE")!!
        val allCountries = listOf("ICNIRP", "CH", "IN", "RU", "US", "IT", "CN")

        allCountries.forEach { code ->
            val other = guidelines.getLimits(code)!!
            assertTrue(
                "Brussels RF should be stricter than $code",
                brussels.rfLimits["wifi_2.4"]!! <= other.rfLimits["wifi_2.4"]!!
            )
        }
    }

    // ========== Measurement Comparison Tests ==========

    @Test
    fun `typical measurement is within all guidelines`() {
        val measurement = createTypicalMeasurement()

        val comparisons = guidelines.compareToAllGuidelines(measurement)

        // Typical indoor measurements should be within all limits
        comparisons.forEach { comparison ->
            assertTrue(
                "Should be within ${comparison.countryName} RF limit",
                comparison.isWithinRfLimit
            )
        }
    }

    @Test
    fun `comparison correctly calculates percentages`() {
        val measurement = createTypicalMeasurement()

        val icnirpComparison = guidelines.compareToGuidelines(measurement, "ICNIRP")
        val swissComparison = guidelines.compareToGuidelines(measurement, "CH")

        // Same exposure, but different percentage of limit
        // Swiss limit is ~100x stricter, so percentage should be ~100x higher
        assertTrue(
            "Swiss percentage should be higher due to stricter limit",
            swissComparison.rfPercentOfLimit > icnirpComparison.rfPercentOfLimit * 10
        )
    }

    @Test
    fun `magnetic field comparison works correctly`() {
        val measurementWithMagnetic = createMeasurementWithMagneticField(75.0) // µT

        val icnirpComparison = guidelines.compareToGuidelines(measurementWithMagnetic, "ICNIRP")
        val swissComparison = guidelines.compareToGuidelines(measurementWithMagnetic, "CH")

        // 75 µT vs ICNIRP limit of 200 µT = 37.5%
        assertEquals(37.5, icnirpComparison.elfPercentOfLimit, 1.0)
        assertTrue(icnirpComparison.isWithinElfLimit)

        // 75 µT vs Swiss limit of 1 µT = 7500% (exceeds limit!)
        assertTrue(swissComparison.elfPercentOfLimit > 100)
        assertFalse(swissComparison.isWithinElfLimit)
    }

    // ========== Format Tests ==========

    @Test
    fun `percentage formatting works correctly`() {
        val measurement = createTypicalMeasurement()
        val comparison = guidelines.compareToGuidelines(measurement, "ICNIRP")

        // Should format to a readable percentage string
        val rfPercent = comparison.formatRfPercent()
        assertTrue("Should contain percent sign", rfPercent.contains("%"))
    }

    // ========== Unit Conversion Tests ==========

    @Test
    fun `V per m to W per m2 conversion is correct`() {
        // E²/377 formula
        // 6 V/m = 36/377 = 0.0955 W/m² (Brussels limit)
        val vPerM = 6.0
        val wPerM2 = RegulatoryGuidelines.Companion.ICNIRP.vPerMToWPerM2(vPerM)

        assertEquals(0.0955, wPerM2, 0.001)
    }

    @Test
    fun `W per m2 to V per m conversion is correct`() {
        // sqrt(W/m² * 377) formula
        // 10 W/m² = sqrt(3770) = 61.4 V/m (ICNIRP limit for 2.4 GHz)
        val wPerM2 = 10.0
        val vPerM = RegulatoryGuidelines.Companion.ICNIRP.wPerM2ToVPerM(wPerM2)

        assertEquals(61.4, vPerM, 0.1)
    }

    // ========== Real-World Scenario Tests ==========

    /**
     * Test scenario: Living near a cell tower
     * Reference: Real measurements typically 0.1-1 V/m at 100m from tower
     */
    @Test
    fun `near cell tower scenario`() {
        val measurement = createMeasurement(
            wifiRssi = listOf(-60), // Weak home WiFi
            cellularRssi = listOf(-50, -55, -60) // Strong cellular from tower
        )

        val icnirpComparison = guidelines.compareToGuidelines(measurement, "ICNIRP")
        val brusselsComparison = guidelines.compareToGuidelines(measurement, "BE")

        // Should be within ICNIRP but might approach Brussels limit
        assertTrue(icnirpComparison.isWithinRfLimit)
        // Brussels has very strict limits
        println("Brussels RF: ${brusselsComparison.formatRfPercent()} of limit")
    }

    /**
     * Test scenario: Office with many WiFi networks
     */
    @Test
    fun `dense office scenario`() {
        val measurement = createMeasurement(
            wifiRssi = (-35 downTo -80 step 5).toList(), // ~10 networks
            cellularRssi = listOf(-70) // Indoor cellular is weak
        )

        val allComparisons = guidelines.compareToAllGuidelines(measurement)

        // Log results for each country
        allComparisons.forEach { comparison ->
            println("${comparison.countryName}: RF ${comparison.formatRfPercent()}")
        }

        // Should be within ICNIRP
        val icnirp = allComparisons.find { it.countryCode == "ICNIRP" }!!
        assertTrue(icnirp.isWithinRfLimit)
    }

    // ========== Helper Functions ==========

    private fun createTypicalMeasurement(): Measurement {
        return createMeasurement(
            wifiRssi = listOf(-45, -55, -65, -70),
            cellularRssi = listOf(-75)
        )
    }

    private fun createMeasurementWithMagneticField(magneticUt: Double): Measurement {
        val wifiSources = listOf(createWifiSource(-50))
        return Measurement(
            id = "test",
            timestamp = Instant.now(),
            eScore = EScoreCalculator().calculate(wifiSources),
            wifiSources = wifiSources,
            bluetoothSources = emptyList(),
            cellularSources = emptyList(),
            magneticField = MagneticFieldReading(
                magnitudeUt = magneticUt,
                magnitudeMg = magneticUt * 10,
                isElevated = magneticUt > 70,
                estimatedAcComponentUt = 5.0,
                confidence = 0.9f
            ),
            context = MeasurementContext(),
            confidence = ConfidenceFlags(0.9f, 0.9f, 0.9f, 0.9f)
        )
    }

    private fun createMeasurement(
        wifiRssi: List<Int>,
        cellularRssi: List<Int>
    ): Measurement {
        val wifiSources = wifiRssi.mapIndexed { i, rssi -> createWifiSource(rssi, i) }
        val cellularSources = cellularRssi.mapIndexed { i, rssi -> createCellularSource(rssi, i) }
        return Measurement(
            id = "test",
            timestamp = Instant.now(),
            eScore = EScoreCalculator().calculate(wifiSources + cellularSources),
            wifiSources = wifiSources,
            bluetoothSources = emptyList(),
            cellularSources = cellularSources,
            magneticField = null,
            context = MeasurementContext(),
            confidence = ConfidenceFlags(0.9f, 0.5f, 0.9f, 0.8f)
        )
    }

    private fun createWifiSource(rssi: Int, index: Int = 0) = WifiSource(
        id = "wifi_$index",
        name = "TestWiFi_$index",
        bssid = "00:11:22:33:44:${index.toString(16).padStart(2, '0')}",
        rssi = rssi,
        frequency = 2437,
        band = WifiBand.WIFI_2_4_GHZ,
        channel = 6,
        isConnected = index == 0,
        contributionPercent = 0
    )

    private fun createCellularSource(rssi: Int, index: Int = 0) = CellularSource(
        id = "cell_$index",
        name = "TestCell",
        rssi = rssi,
        technology = CellularTechnology.LTE_4G,
        band = 7,
        cellId = 12345 + index,
        isRegistered = index == 0,
        contributionPercent = 0
    )
}
