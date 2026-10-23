package com.openemf.ui.model

import com.openemf.data.database.ObservationRecord
import com.openemf.sensors.api.*
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

enum class HistoryRange(val label: String, val seconds: Long) {
    HOUR("1 hour", 3600), DAY("24 hours", 86400), WEEK("7 days", 604800)
}

enum class SignalMetric(val label: String, val unit: String) {
    RSSI("RSSI", "dBm"), RSRP("RSRP", "dBm"), SS_RSRP("SS-RSRP", "dBm"),
    PLATFORM_DBM("Platform power", "dBm"), RSRQ("RSRQ", "dB"), RSSNR("RSSNR", "dB"),
    SS_RSRQ("SS-RSRQ", "dB"), SS_SINR("SS-SINR", "dB"),
    CSI_RSRP("CSI-RSRP", "dBm"), CSI_RSRQ("CSI-RSRQ", "dB"), CSI_SINR("CSI-SINR", "dB");

    fun value(record: ObservationRecord): Double? {
        val o = record.observation
        val d = o.cellular?.details
        return when (this) {
            RSSI, PLATFORM_DBM ->
                o.signalDbm?.toDouble()?.takeIf { o.signalMetric == name }
            RSRP -> d?.rsrp?.toDouble() ?: o.signalDbm?.toDouble()?.takeIf { o.signalMetric == name }
            SS_RSRP -> d?.ssRsrp?.toDouble() ?: o.signalDbm?.toDouble()?.takeIf { o.signalMetric == name }
            RSRQ -> d?.rsrq?.toDouble()
            RSSNR -> d?.rssnrDb
            SS_RSRQ -> d?.ssRsrq?.toDouble()
            SS_SINR -> d?.ssSinr?.toDouble()
            CSI_RSRP -> d?.csiRsrp?.toDouble()
            CSI_RSRQ -> d?.csiRsrq?.toDouble()
            CSI_SINR -> d?.csiSinr?.toDouble()
        }
    }
}

data class HistoryPoint(val time: Instant, val value: Double, val record: ObservationRecord)
data class SignalSeries(
    val metric: SignalMetric,
    val points: List<HistoryPoint>,
    val excludedAppearances: Int,
    val repeatedAppearances: Int
)

/** Plot observation time, never cache-read time. Keep all appearances in the raw timeline. */
fun signalSeries(records: List<ObservationRecord>, metric: SignalMetric, since: Instant, until: Instant): SignalSeries {
    val seen = mutableSetOf<String>()
    var excluded = 0
    var repeated = 0
    val points = records.sortedWith(compareByDescending<ObservationRecord> { it.capturedAt }
        .thenByDescending { it.observation.rowId }).mapNotNull { record ->
        val o = record.observation
        val time = o.observedAt
        val value = metric.value(record)
        if (time == null || time < since || time > until || o.timeBasis == ObservationTimeBasis.UNKNOWN ||
            o.freshnessAtCapture != Freshness.FRESH || value == null || !value.isFinite()) {
            excluded++
            null
        } else {
            val clockKey = if (record.bootCount != null && o.observedElapsedRealtimeMs != null)
                "boot:${record.bootCount}:${o.observedElapsedRealtimeMs}" else "wall:${time.toEpochMilli()}"
            val key = "${o.sourceType}:${o.sourceId}:${metric.name}:$clockKey"
            if (!seen.add(key)) { repeated++; null } else HistoryPoint(time, value, record)
        }
    }.sortedBy { it.time }
    return SignalSeries(metric, points, excluded, repeated)
}

data class PlotBounds(val start: Instant, val end: Instant, val low: Double, val high: Double) {
    init { require(end > start && high > low && low.isFinite() && high.isFinite()) }
    fun x(time: Instant): Float = ((time.toEpochMilli() - start.toEpochMilli()).toDouble() /
        (end.toEpochMilli() - start.toEpochMilli())).toFloat().coerceIn(0f, 1f)
    fun y(value: Double): Float = ((high - value) / (high - low)).toFloat().coerceIn(0f, 1f)
}

fun plotBounds(points: List<HistoryPoint>, since: Instant, until: Instant): PlotBounds {
    require(points.isNotEmpty())
    val low = floor((points.minOf { it.value } - 2) / 5) * 5
    val high = ceil((points.maxOf { it.value } + 2) / 5) * 5
    return PlotBounds(since, until, low, high)
}
