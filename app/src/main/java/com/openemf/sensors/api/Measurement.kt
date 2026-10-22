package com.openemf.sensors.api

import java.time.Instant

/**
 * A complete EMF measurement with all sources and calculated E-Score.
 */
data class Measurement(
    val id: String,
    val timestamp: Instant,
    val eScore: EScoreResult,
    val wifiSources: List<WifiSource>,
    val bluetoothSources: List<BluetoothSource>,
    val cellularSources: List<CellularSource>,
    val magneticField: MagneticFieldReading?,
    val context: MeasurementContext,
    val confidence: ConfidenceFlags,
    val acquisition: Map<Modality, AcquisitionReport> = emptyMap(),
    val pairedBluetoothDevices: List<PairedBluetoothDevice> = emptyList()
) {
    val totalSources: Int
        get() = wifiSources.size + bluetoothSources.size + cellularSources.size
}

/**
 * Magnetic field (ELF-EMF) reading from magnetometer.
 *
 * IMPORTANT: This measures LOW FREQUENCY magnetic fields, not RF-EMF.
 * - Power lines, wiring, motors operate at 50/60 Hz
 * - WiFi/cellular are RF at millions of Hz
 * - Phone magnetometers have limited accuracy for EMF measurement
 */
data class MagneticFieldReading(
    val magnitudeUt: Double,           // Total field in microtesla
    val magnitudeMg: Double,           // Total field in milligauss (1 µT = 10 mG)
    val isElevated: Boolean,           // Above typical Earth field
    val estimatedAcComponentUt: Double, // Fluctuation (rough AC estimate)
    val confidence: Float              // 0-1 confidence in reading
) {
    /**
     * Get severity level for display.
     */
    val severityLevel: MagneticSeverity
        get() = when {
            magnitudeUt < 50 -> MagneticSeverity.NORMAL
            magnitudeUt < 100 -> MagneticSeverity.SLIGHTLY_ELEVATED
            magnitudeUt < 200 -> MagneticSeverity.ELEVATED
            magnitudeUt < 500 -> MagneticSeverity.HIGH
            else -> MagneticSeverity.VERY_HIGH
        }
}

enum class MagneticSeverity {
    NORMAL,           // ~Earth's field (25-65 µT)
    SLIGHTLY_ELEVATED, // 50-100 µT
    ELEVATED,         // 100-200 µT
    HIGH,             // 200-500 µT
    VERY_HIGH         // >500 µT
}

/**
 * Complete E-Score calculation result.
 * Based on ElectroSmart algorithm (Inria, France).
 */
data class EScoreResult(
    val score: Int,                           // 0-100
    val level: ExposureLevel,                 // LOW, MODERATE, HIGH
    val cumulativeDbm: Int,                   // Total in dBm
    val cumulativeMw: Double,                 // Total in milliwatts
    val breakdown: Map<SourceType, Int>,      // Percentage by type
    val dominantSource: Source?,              // Highest contributor
    val sourceCount: Int                      // Number of valid sources
) {
    val isLow: Boolean get() = level == ExposureLevel.LOW
    val isModerate: Boolean get() = level == ExposureLevel.MODERATE
    val isHigh: Boolean get() = level == ExposureLevel.HIGH
}

/**
 * Exposure level classification.
 * Three clear levels matching ElectroSmart research thresholds.
 */
enum class ExposureLevel {
    LOW,      // < -75 dBm, score < 43, green
    MODERATE, // -75 to -34 dBm, score 43-71, orange
    HIGH      // > -34 dBm, score > 71, red
}

enum class SourceType {
    WIFI,
    BLUETOOTH,
    CELLULAR
}

interface Source {
    val id: String
    val name: String?
    val rssi: Int?
    val observation: Observation
    val type: SourceType
    val contributionPercent: Int
}

data class WifiSource(
    override val id: String,
    override val name: String?,              // SSID (may be null on Android 10+)
    val bssid: String,
    override val rssi: Int?,
    val frequency: Int,                      // MHz
    val band: WifiBand,
    val channel: Int,
    val channelWidth: WifiChannelWidth = WifiChannelWidth.UNKNOWN,
    val standard: WifiStandard = WifiStandard.UNKNOWN,
    val security: WifiSecurity = WifiSecurity.UNKNOWN,
    val isConnected: Boolean = false,
    override val contributionPercent: Int = 0,
    override val observation: Observation = Observation()
) : Source {
    override val type = SourceType.WIFI
}

enum class WifiBand {
    WIFI_2_4_GHZ,
    WIFI_5_GHZ,
    WIFI_6_GHZ,
    UNKNOWN
}

enum class WifiChannelWidth {
    WIDTH_20_MHZ,
    WIDTH_40_MHZ,
    WIDTH_80_MHZ,
    WIDTH_160_MHZ,
    WIDTH_80_PLUS_80_MHZ,
    WIDTH_320_MHZ,  // WiFi 7
    UNKNOWN
}

enum class WifiStandard {
    LEGACY,      // 802.11a/b/g
    WIFI_4,      // 802.11n (Wi-Fi 4)
    WIFI_5,      // 802.11ac (Wi-Fi 5)
    WIFI_6,      // 802.11ax (Wi-Fi 6)
    WIFI_6E,     // 802.11ax in 6GHz (Wi-Fi 6E)
    WIFI_7,      // 802.11be (Wi-Fi 7)
    UNKNOWN
}

enum class WifiSecurity {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3,
    WPA2_WPA3,   // Transition mode
    UNKNOWN
}

data class BluetoothSource(
    override val id: String,
    override val name: String?,
    val address: String,
    override val rssi: Int?,
    val deviceType: BluetoothDeviceType,
    val isConnected: Boolean = false,
    override val contributionPercent: Int = 0,
    override val observation: Observation = Observation(),
    val transport: BluetoothTransport = BluetoothTransport.UNKNOWN
) : Source {
    override val type = SourceType.BLUETOOTH
}

enum class BluetoothDeviceType {
    AIRTAG,
    AIRPODS,
    SMARTWATCH,
    FITNESS_BAND,
    SPEAKER,
    HEADPHONES,
    PHONE,
    COMPUTER,
    UNKNOWN
}

data class CellularSource(
    override val id: String,
    override val name: String?,              // Carrier name
    override val rssi: Int?,                 // Platform dBm (LTE RSRP / NR SS-RSRP); null if unavailable
    val technology: CellularTechnology,
    val band: Int?,
    val cellId: Long?,
    val isRegistered: Boolean = false,
    override val contributionPercent: Int = 0,
    // Detailed cell identity (Phase 3)
    val details: CellularDetails? = null,
    override val observation: Observation = Observation(),
    val subscriptionId: Int? = null
) : Source {
    override val type = SourceType.CELLULAR
}

/**
 * Detailed cellular information like ElectroSmart displays.
 * Available on LTE/5G networks.
 */
data class CellularDetails(
    // Identity
    val mcc: Int? = null,                    // Mobile Country Code (e.g., 404 = India)
    val mnc: Int? = null,                    // Mobile Network Code (e.g., 49 = Airtel)
    val tac: Int? = null,                    // Tracking Area Code (LTE) / LAC (3G)
    val ci: Int? = null,                     // Cell Identity (28-bit for LTE)
    val pci: Int? = null,                    // Physical Cell ID (0-503 for LTE)
    val enb: Int? = null,                    // eNodeB ID (derived from CI)
    val eci: Long? = null,                   // E-UTRAN Cell ID (full 28-bit)
    // Signal quality
    val rsrp: Int? = null,                   // Reference Signal Received Power (dBm)
    val rsrq: Int? = null,                   // Reference Signal Received Quality (dB)
    val rssnr: Int? = null,                  // Raw RSSNR: tenths of dB before API 30, whole dB afterwards
    val cqi: Int? = null,                    // Channel Quality Indicator (0-15)
    val ta: Int? = null,                     // Timing Advance
    // Network info
    val earfcn: Int? = null,                 // E-UTRA Absolute Radio Frequency Channel Number
    val bandwidth: Int? = null,              // Channel bandwidth in MHz (e.g., 10, 15, 20)
    val plmn: String? = null,                // Original strings preserve 2- vs 3-digit MNC
    val nci: Long? = null,
    val nrArfcn: Int? = null,
    val bands: List<Int> = emptyList(),
    val ssRsrp: Int? = null,
    val ssRsrq: Int? = null,
    val ssSinr: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
    val lac: Int? = null,
    val arfcn: Int? = null,
    val uarfcn: Int? = null,
    val rssnrDb: Double? = null
) {
    /**
     * Human-readable PLMN (e.g., "404-49")
     */
    val formattedPlmn: String?
        get() = plmn?.takeIf { it.length in 5..6 }?.let { "${it.take(3)}-${it.drop(3)}" }
            ?: if (mcc != null && mnc != null) "$mcc-$mnc" else plmn

    /**
     * Calculate eNodeB ID from ECI (ECI = eNB * 256 + CID)
     */
    val calculatedEnb: Int?
        get() = eci?.let { (it / 256).toInt() }

    /**
     * Local cell ID within eNodeB
     */
    val localCellId: Int?
        get() = eci?.let { (it % 256).toInt() }
}

enum class CellularTechnology {
    GSM_2G,
    CDMA_2G,
    UMTS_3G,
    LTE_4G,
    NR_5G,
    UNKNOWN
}

data class MeasurementContext(
    val motionState: MotionState = MotionState.UNKNOWN,
    val isForeground: Boolean = true,
    val isVehicleMode: Boolean = false,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val locationHash: String? = null
)

enum class MotionState {
    STATIONARY,
    WALKING,
    RUNNING,
    CYCLING,
    DRIVING,
    UNKNOWN
}
