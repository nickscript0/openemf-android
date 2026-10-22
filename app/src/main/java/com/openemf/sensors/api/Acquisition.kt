package com.openemf.sensors.api

import java.time.Instant

enum class Freshness { FRESH, STALE, UNKNOWN }
enum class ObservationTimeBasis { HARDWARE, EVENT_RECEIPT, UNKNOWN }

/** Hardware observation time is independent of the time the app reads a cache. */
data class Observation(
    val elapsedRealtimeMs: Long? = null,
    val timestamp: Instant? = null,
    val cached: Boolean = false,
    val freshForMs: Long = 120_000,
    val timeBasis: ObservationTimeBasis = ObservationTimeBasis.UNKNOWN
) {
    fun ageMs(nowElapsedMs: Long): Long? = elapsedRealtimeMs
        ?.takeIf { it > 0 && it <= nowElapsedMs }
        ?.let { nowElapsedMs - it }

    fun freshness(nowElapsedMs: Long): Freshness = ageMs(nowElapsedMs)?.let {
        if (it <= freshForMs) Freshness.FRESH else Freshness.STALE
    } ?: Freshness.UNKNOWN

    companion object {
        /** Wall time is an estimate derived at read time; retain the raw boot-clock time. */
        fun fromBootTime(observedMs: Long, nowMs: Long, now: Instant,
                         cached: Boolean = false, freshForMs: Long = 120_000,
                         timeBasis: ObservationTimeBasis = ObservationTimeBasis.HARDWARE): Observation {
            val valid = observedMs.takeIf { it > 0 && it <= nowMs }
            return Observation(valid, valid?.let { now.minusMillis(nowMs - it) }, cached, freshForMs,
                if (valid == null) ObservationTimeBasis.UNKNOWN else timeBasis)
        }
    }
}

enum class AcquisitionStatus {
    UPDATED, PARTIAL, CACHED, THROTTLED, REFUSED, TIMED_OUT, PERMISSION_DENIED,
    RADIO_DISABLED, LOCATION_DISABLED, UNAVAILABLE, FAILED, NOT_REQUESTED
}

data class AcquisitionReport(val status: AcquisitionStatus, val detail: String? = null)

data class AcquisitionResult<T : Source>(
    val sources: List<T>,
    val report: AcquisitionReport
) {
    constructor(sources: List<T>, status: AcquisitionStatus, detail: String? = null) :
        this(sources, AcquisitionReport(status, detail))
}

/** Pairing is inventory, not evidence of a received signal or an active connection. */
data class PairedBluetoothDevice(val address: String, val name: String?)

enum class BluetoothTransport { BLE, CLASSIC, UNKNOWN }

/** Android's unavailable sentinels must not become measurements. */
fun availableInt(value: Int): Int? = value.takeUnless { it == Int.MAX_VALUE }
fun availableNci(value: Long): Long? = value.takeIf { it in 0..68_719_476_735L }

/** Android 11 changed LTE RSSNR from tenths of a dB to whole dB. Keep the raw value too. */
fun lteRssnrDb(raw: Int, api: Int): Double? = availableInt(raw)?.let {
    if (api < 30) it / 10.0 else it.toDouble()
}

fun cellularIdentity(technology: CellularTechnology, subscription: Int?, plmn: String?,
                     area: Int?, cell: Long?, pci: Int?, channel: Int?): String {
    val scope = listOf("cell", technology.name, subscription, plmn)
    val identity = when {
        cell == null -> listOf("unknown", area, pci, channel)
        technology == CellularTechnology.LTE_4G || technology == CellularTechnology.NR_5G -> listOf(cell)
        else -> listOf(area, cell)
    }
    return (scope + identity).joinToString(":") { it?.toString() ?: "unknown" }
}
