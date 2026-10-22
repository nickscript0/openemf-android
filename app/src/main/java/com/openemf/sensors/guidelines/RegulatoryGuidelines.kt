package com.openemf.sensors.guidelines

import com.openemf.sensors.api.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Regulatory guidelines for EMF exposure limits by country.
 *
 * Includes limits from:
 * - ICNIRP (International Commission on Non-Ionizing Radiation Protection)
 * - FCC (US Federal Communications Commission)
 * - Various national standards (Switzerland, Belgium, India, etc.)
 *
 * Note: Most limits are for THERMAL effects. Non-thermal bioeffects are debated.
 */
@Singleton
class RegulatoryGuidelines @Inject constructor() {

    companion object {
        // ICNIRP 2020 reference levels for general public
        // RF-EMF power density limits (W/m²) by frequency
        object ICNIRP {
            // 400-2000 MHz: f/200 W/m²
            val RF_900_MHZ = 4.5    // W/m²
            val RF_1800_MHZ = 9.0   // W/m²
            val RF_2450_MHZ = 10.0  // W/m²
            val RF_5000_MHZ = 10.0  // W/m²

            // ELF magnetic field (50/60 Hz)
            val ELF_MAGNETIC = 200.0 // µT

            // Convert V/m to W/m²: S = E²/377
            fun vPerMToWPerM2(vPerM: Double): Double = (vPerM * vPerM) / 377.0
            fun wPerM2ToVPerM(wPerM2: Double): Double = sqrt(wPerM2 * 377.0)
        }

        // Country-specific limits (can be stricter than ICNIRP)
        data class CountryLimits(
            val code: String,
            val name: String,
            val framework: String,
            val rfLimits: Map<String, Double>, // Frequency band -> W/m²
            val elfMagnetic: Double,            // µT for 50/60 Hz
            val notes: String
        )

        val COUNTRIES = listOf(
            CountryLimits(
                code = "ICNIRP",
                name = "ICNIRP International",
                framework = "ICNIRP 2020",
                rfLimits = mapOf(
                    "wifi_2.4" to 10.0,
                    "wifi_5" to 10.0,
                    "lte_700" to 3.5,
                    "lte_1800" to 9.0,
                    "lte_2600" to 10.0,
                    "nr_3500" to 10.0
                ),
                elfMagnetic = 200.0,
                notes = "International standard based on thermal effects"
            ),
            CountryLimits(
                code = "CH",
                name = "Switzerland",
                framework = "Swiss OMEN",
                rfLimits = mapOf(
                    "wifi_2.4" to 0.1,
                    "wifi_5" to 0.1,
                    "lte_700" to 0.042,
                    "lte_1800" to 0.095,
                    "lte_2600" to 0.1,
                    "nr_3500" to 0.1
                ),
                elfMagnetic = 1.0,
                notes = "Installation limits ~100x stricter for sensitive areas"
            ),
            CountryLimits(
                code = "BE",
                name = "Belgium (Brussels)",
                framework = "Brussels Regional",
                rfLimits = mapOf(
                    "wifi_2.4" to 0.024,
                    "wifi_5" to 0.024,
                    "lte_700" to 0.024,
                    "lte_1800" to 0.024,
                    "lte_2600" to 0.024,
                    "nr_3500" to 0.024
                ),
                elfMagnetic = 10.0,
                notes = "Strictest RF limits globally at 6 V/m cumulative"
            ),
            CountryLimits(
                code = "IN",
                name = "India",
                framework = "DoT 2012",
                rfLimits = mapOf(
                    "wifi_2.4" to 1.0,
                    "wifi_5" to 1.0,
                    "lte_700" to 0.35,
                    "lte_1800" to 0.9,
                    "lte_2600" to 1.0,
                    "nr_3500" to 1.0
                ),
                elfMagnetic = 100.0,
                notes = "ICNIRP/10 since 2012"
            ),
            CountryLimits(
                code = "RU",
                name = "Russia",
                framework = "SanPiN",
                rfLimits = mapOf(
                    "wifi_2.4" to 0.1,
                    "wifi_5" to 0.1,
                    "lte_700" to 0.1,
                    "lte_1800" to 0.1,
                    "lte_2600" to 0.1,
                    "nr_3500" to 0.1
                ),
                elfMagnetic = 10.0,
                notes = "Based on Soviet-era research on non-thermal effects"
            ),
            CountryLimits(
                code = "US",
                name = "United States",
                framework = "FCC OET-65",
                rfLimits = mapOf(
                    "wifi_2.4" to 10.0,
                    "wifi_5" to 10.0,
                    "lte_700" to 4.67,
                    "lte_1800" to 10.0,
                    "lte_2600" to 10.0,
                    "nr_3500" to 10.0
                ),
                elfMagnetic = 904.0, // 60 Hz, very permissive
                notes = "FCC limits unchanged since 1996"
            ),
            CountryLimits(
                code = "IT",
                name = "Italy",
                framework = "DPCM 8/7/2003",
                rfLimits = mapOf(
                    "wifi_2.4" to 0.1,
                    "wifi_5" to 0.1,
                    "lte_700" to 0.1,
                    "lte_1800" to 0.1,
                    "lte_2600" to 0.1,
                    "nr_3500" to 0.1
                ),
                elfMagnetic = 3.0,
                notes = "3-tier system with attention value at 6 V/m"
            ),
            CountryLimits(
                code = "CN",
                name = "China",
                framework = "GB 8702-2014",
                rfLimits = mapOf(
                    "wifi_2.4" to 0.4,
                    "wifi_5" to 0.4,
                    "lte_700" to 0.4,
                    "lte_1800" to 0.4,
                    "lte_2600" to 0.4,
                    "nr_3500" to 0.4
                ),
                elfMagnetic = 100.0,
                notes = "Flat 0.4 W/m² across frequencies"
            )
        )

        private val countryMap = COUNTRIES.associateBy { it.code }
    }

    /**
     * Get limits for a specific country.
     */
    fun getLimits(countryCode: String): CountryLimits? {
        return countryMap[countryCode.uppercase()]
    }

    /**
     * Compare a measurement against a country's guidelines.
     */
    fun compareToGuidelines(
        measurement: Measurement,
        countryCode: String = "ICNIRP"
    ): GuidelinesComparison {
        val limits = getLimits(countryCode) ?: getLimits("ICNIRP")!!

        // Calculate RF exposure from sources
        val wifiExposure = calculateWifiExposure(measurement.wifiSources)
        val cellularExposure = calculateCellularExposure(measurement.cellularSources)
        val totalRfExposure = wifiExposure + cellularExposure

        // Get appropriate limit (use WiFi 2.4 GHz as reference)
        val rfLimit = limits.rfLimits["wifi_2.4"] ?: ICNIRP.RF_2450_MHZ

        // Magnetic field comparison
        val magneticField = measurement.magneticField?.magnitudeUt ?: 0.0
        val elfLimit = limits.elfMagnetic

        return GuidelinesComparison(
            countryCode = limits.code,
            countryName = limits.name,
            framework = limits.framework,
            rfExposureWPerM2 = totalRfExposure,
            rfLimitWPerM2 = rfLimit,
            rfPercentOfLimit = (totalRfExposure / rfLimit * 100).coerceAtMost(999.0),
            elfExposureUt = magneticField,
            elfLimitUt = elfLimit,
            elfPercentOfLimit = (magneticField / elfLimit * 100).coerceAtMost(999.0),
            isWithinRfLimit = totalRfExposure <= rfLimit,
            isWithinElfLimit = magneticField <= elfLimit,
            notes = limits.notes
        )
    }

    /**
     * Compare measurement against multiple countries.
     */
    fun compareToAllGuidelines(measurement: Measurement): List<GuidelinesComparison> {
        return COUNTRIES.map { country ->
            compareToGuidelines(measurement, country.code)
        }
    }

    /**
     * Estimate WiFi power density at phone location from RSSI.
     *
     * This is a rough estimate. Actual power density depends on:
     * - Transmitter power (typically 100mW for WiFi)
     * - Distance (unknown from RSSI alone due to multipath)
     * - Antenna patterns
     *
     * Using simplified free-space path loss model:
     * P_received (dBm) = P_transmitted (dBm) - PathLoss
     * Power density ≈ P_received / effective_area
     */
    private fun calculateWifiExposure(sources: List<WifiSource>): Double {
        if (sources.isEmpty()) return 0.0

        // Sum power from all sources (linear, not dB)
        val totalPowerMw = sources.sumOf { source ->
            source.rssi?.let { dbmToMilliwatts(it) } ?: 0.0
        }

        // Convert mW to W/m² (rough estimate)
        // Assuming effective area ~0.01 m² for phone antenna at 2.4 GHz
        val effectiveArea = 0.01
        return totalPowerMw / 1000.0 / effectiveArea
    }

    /**
     * Estimate cellular power density from RSSI.
     */
    private fun calculateCellularExposure(sources: List<CellularSource>): Double {
        if (sources.isEmpty()) return 0.0

        // Sum power from all sources
        val totalPowerMw = sources.sumOf { source ->
            source.rssi?.let { dbmToMilliwatts(it) } ?: 0.0
        }

        // Cellular has lower frequency, larger effective area ~0.1 m²
        val effectiveArea = 0.1
        return totalPowerMw / 1000.0 / effectiveArea
    }

    private fun dbmToMilliwatts(dbm: Int): Double {
        return Math.pow(10.0, dbm / 10.0)
    }
}

/**
 * Result of comparing measurement to regulatory guidelines.
 */
data class GuidelinesComparison(
    val countryCode: String,
    val countryName: String,
    val framework: String,
    val rfExposureWPerM2: Double,
    val rfLimitWPerM2: Double,
    val rfPercentOfLimit: Double,
    val elfExposureUt: Double,
    val elfLimitUt: Double,
    val elfPercentOfLimit: Double,
    val isWithinRfLimit: Boolean,
    val isWithinElfLimit: Boolean,
    val notes: String
) {
    val isFullyCompliant: Boolean
        get() = isWithinRfLimit && isWithinElfLimit

    /**
     * Format RF exposure as percentage string.
     */
    fun formatRfPercent(): String {
        return when {
            rfPercentOfLimit < 0.01 -> "<0.01%"
            rfPercentOfLimit < 1 -> String.format("%.2f%%", rfPercentOfLimit)
            rfPercentOfLimit < 10 -> String.format("%.1f%%", rfPercentOfLimit)
            else -> String.format("%.0f%%", rfPercentOfLimit)
        }
    }

    /**
     * Format ELF exposure as percentage string.
     */
    fun formatElfPercent(): String {
        return when {
            elfPercentOfLimit < 0.01 -> "<0.01%"
            elfPercentOfLimit < 1 -> String.format("%.2f%%", elfPercentOfLimit)
            elfPercentOfLimit < 10 -> String.format("%.1f%%", elfPercentOfLimit)
            else -> String.format("%.0f%%", elfPercentOfLimit)
        }
    }
}
