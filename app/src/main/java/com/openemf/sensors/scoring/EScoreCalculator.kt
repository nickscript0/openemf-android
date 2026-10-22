package com.openemf.sensors.scoring

import com.openemf.sensors.api.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * E-Score Calculator - Validated Algorithm
 *
 * Based on ElectroSmart (Inria, France, 2015-2022)
 * https://github.com/arnaudlegout/electrosmart
 *
 * Key principle: Power adds LINEARLY (in milliwatts), not logarithmically (in dBm).
 * Two -40 dBm signals = -37 dBm (double power = +3 dB), NOT -80 dBm.
 *
 * Score formula: score = floor(2 * (totalDbm + 140) / 3)
 * - Every 3 dB (doubling of power) = ~2 score points
 * - Range: 0 at -140 dBm (noise floor) to 100 at +10 dBm
 */
@Singleton
class EScoreCalculator @Inject constructor() {

    companion object {
        // Score calculation constants (from ElectroSmart Const.java)
        const val MIN_DBM_FOR_SCORE = -140  // Noise floor
        const val MAX_DBM_FOR_SCORE = 10    // Theoretical max

        // Level thresholds (from ElectroSmart - research-validated)
        const val LOW_THRESHOLD_DBM = -75   // Below this = LOW (green)
        const val HIGH_THRESHOLD_DBM = -34  // Above this = HIGH (red)

        // Corresponding scores
        const val LOW_THRESHOLD_SCORE = 43   // score at -75 dBm
        const val HIGH_THRESHOLD_SCORE = 71  // score at -34 dBm

        // Valid signal ranges per technology (from ElectroSmart Const.java)
        val WIFI_RANGE = -150..-1
        val BLUETOOTH_RANGE = -150..-1
        val GSM_RANGE = -113..-51
        val WCDMA_RANGE = -120..-24
        val LTE_RANGE = -140..-43
        val NR_RANGE = -156..-31  // Android NR SS-RSRP range
        val CDMA_RANGE = -120..0
    }

    /**
     * Calculate E-Score from all detected sources.
     *
     * Algorithm:
     * 1. Filter to valid signals only
     * 2. Convert each source's dBm to milliwatts
     * 3. Sum all milliwatts (power adds linearly)
     * 4. Convert total back to dBm
     * 5. Apply linear score formula
     */
    fun calculate(sources: List<Source>): EScoreResult {
        if (sources.isEmpty()) {
            return EScoreResult(
                score = 0,
                level = ExposureLevel.LOW,
                cumulativeDbm = MIN_DBM_FOR_SCORE,
                cumulativeMw = 0.0,
                breakdown = emptyMap(),
                dominantSource = null,
                sourceCount = 0
            )
        }

        // Filter to valid signals only
        val validSources = sources.filter { isValidSignal(it) }

        if (validSources.isEmpty()) {
            return EScoreResult(
                score = 0,
                level = ExposureLevel.LOW,
                cumulativeDbm = MIN_DBM_FOR_SCORE,
                cumulativeMw = 0.0,
                breakdown = emptyMap(),
                dominantSource = null,
                sourceCount = 0
            )
        }

        // Step 1 & 2: Sum power in milliwatts per source type
        val mwByType = mutableMapOf<SourceType, Double>()
        var totalMw = 0.0

        for (source in validSources) {
            val mw = dbmToMilliwatts(requireNotNull(source.rssi))
            totalMw += mw
            mwByType[source.type] = (mwByType[source.type] ?: 0.0) + mw
        }

        // Step 3: Convert total back to dBm
        val totalDbm = milliwattsToDbm(totalMw)

        // Step 4: Calculate score using ElectroSmart formula
        val score = calculateScore(totalDbm)

        // Determine exposure level
        val level = dbmToLevel(totalDbm)

        // Calculate breakdown percentages
        val breakdown = if (totalMw > 0) {
            mwByType.mapValues { (_, mw) ->
                ((mw / totalMw) * 100).roundToInt()
            }
        } else {
            emptyMap()
        }

        // Find dominant source (highest power)
        val dominant = validSources.maxByOrNull { dbmToMilliwatts(requireNotNull(it.rssi)) }

        return EScoreResult(
            score = score,
            level = level,
            cumulativeDbm = totalDbm,
            cumulativeMw = totalMw,
            breakdown = breakdown,
            dominantSource = dominant,
            sourceCount = validSources.size
        )
    }

    /**
     * ElectroSmart score formula:
     * score = floor(2 * (dBm - MIN_DBM) / 3)
     *
     * This creates a linear relationship where every +3 dB
     * (doubling of power) adds ~2 points to score.
     */
    private fun calculateScore(dbm: Int): Int {
        return when {
            dbm < MIN_DBM_FOR_SCORE -> 0
            dbm > MAX_DBM_FOR_SCORE -> 100
            else -> {
                val score = floor(2.0 * (dbm - MIN_DBM_FOR_SCORE) / 3.0).toInt()
                score.coerceIn(0, 100)
            }
        }
    }

    /**
     * Classify exposure level based on cumulative dBm.
     * Thresholds from ElectroSmart research.
     */
    private fun dbmToLevel(dbm: Int): ExposureLevel {
        return when {
            dbm < LOW_THRESHOLD_DBM -> ExposureLevel.LOW      // < -75 dBm
            dbm < HIGH_THRESHOLD_DBM -> ExposureLevel.MODERATE // -75 to -34 dBm
            else -> ExposureLevel.HIGH                         // > -34 dBm
        }
    }

    /**
     * Check if signal is within valid range for its technology.
     */
    fun isValidSignal(source: Source): Boolean {
        val range = when (source) {
            is WifiSource -> WIFI_RANGE
            is BluetoothSource -> BLUETOOTH_RANGE
            is CellularSource -> when (source.technology) {
                CellularTechnology.GSM_2G -> GSM_RANGE
                CellularTechnology.CDMA_2G -> CDMA_RANGE
                CellularTechnology.UMTS_3G -> WCDMA_RANGE
                CellularTechnology.LTE_4G -> LTE_RANGE
                CellularTechnology.NR_5G -> NR_RANGE
                else -> LTE_RANGE
            }
            else -> -150..0
        }
        return source.rssi?.let { it in range } == true
    }

    fun calculateFresh(sources: List<Source>, nowElapsedMs: Long): EScoreResult =
        calculate(sources.filter { it.observation.freshness(nowElapsedMs) == Freshness.FRESH })

    // ==================== Conversion Utilities ====================

    /**
     * Convert dBm to milliwatts.
     * Formula: mW = 10^(dBm/10)
     */
    fun dbmToMilliwatts(dbm: Int): Double {
        return 10.0.pow(dbm / 10.0)
    }

    /**
     * Convert milliwatts to dBm.
     * Formula: dBm = 10 * log10(mW)
     */
    fun milliwattsToDbm(mw: Double): Int {
        return if (mw > 0) {
            (10.0 * log10(mw)).roundToInt()
        } else {
            MIN_DBM_FOR_SCORE - 1
        }
    }

    /**
     * Convert dBm to human-readable power with SI prefix.
     * Examples: "1 mW", "1 µW", "1 nW", "1 pW"
     */
    fun dbmToWattString(dbm: Int): String {
        val mw = dbmToMilliwatts(dbm)

        return when {
            mw >= 1.0 -> String.format("%.1f mW", mw)
            mw >= 0.001 -> String.format("%.1f µW", mw * 1000)
            mw >= 0.000001 -> String.format("%.1f nW", mw * 1_000_000)
            mw >= 0.000000001 -> String.format("%.1f pW", mw * 1_000_000_000)
            else -> String.format("%.1f fW", mw * 1_000_000_000_000)
        }
    }

    /**
     * Convert dBm to percentage (linear mapping for UI display).
     * Range: -140 dBm = 0%, +10 dBm = 100%
     */
    fun dbmToPercentage(dbm: Int): Int {
        return when {
            dbm < MIN_DBM_FOR_SCORE -> 0
            dbm > MAX_DBM_FOR_SCORE -> 100
            else -> {
                val range = MAX_DBM_FOR_SCORE - MIN_DBM_FOR_SCORE
                ((dbm - MIN_DBM_FOR_SCORE).toFloat() / range * 100).roundToInt()
            }
        }
    }

    // ==================== UI Helpers ====================

    /**
     * Get color for score visualization (3-color scheme).
     */
    fun getScoreColor(score: Int): Long = when {
        score < LOW_THRESHOLD_SCORE -> 0xFF22C55E   // Green
        score < HIGH_THRESHOLD_SCORE -> 0xFFF59E0B  // Orange/Amber
        else -> 0xFFEF4444                          // Red
    }

    /**
     * Get color for exposure level.
     */
    fun getLevelColor(level: ExposureLevel): Long = when (level) {
        ExposureLevel.LOW -> 0xFF22C55E      // Green
        ExposureLevel.MODERATE -> 0xFFF59E0B // Orange/Amber
        ExposureLevel.HIGH -> 0xFFEF4444     // Red
    }

    /**
     * Get human-readable label for score.
     */
    fun getScoreLabel(score: Int): String = when {
        score < LOW_THRESHOLD_SCORE -> "Low"
        score < HIGH_THRESHOLD_SCORE -> "Moderate"
        else -> "High"
    }
}
