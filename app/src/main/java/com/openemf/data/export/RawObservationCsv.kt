package com.openemf.data.export

import com.openemf.data.database.ObservationRecord
import java.io.Writer

/** UTF-8 is selected by the caller. UTC timestamps, explicit units, RFC 4180 quoting and CRLF. */
object RawObservationCsv {
    private data class Column(val header: String, val value: (ObservationRecord) -> Any?)
    private val columns = listOf(
        Column("observation_id") { it.observation.rowId },
        Column("capture_id") { it.observation.measurementId },
        Column("captured_at_utc") { it.capturedAt },
        Column("capture_mode") { it.captureMode },
        Column("capture_is_foreground") { it.isForeground },
        Column("capture_motion_state") { it.motionState },
        Column("recording_session_id") { it.recordingSessionId },
        Column("boot_count") { it.bootCount },
        Column("capture_elapsed_realtime_ms") { it.captureElapsedRealtimeMs },
        Column("source_type") { it.observation.sourceType },
        Column("source_id") { it.observation.sourceId },
        Column("source_name") { it.observation.name },
        Column("signal_metric") { it.observation.signalMetric },
        Column("signal_dbm") { it.observation.signalDbm },
        Column("observed_at_utc_estimated") { it.observation.observedAt },
        Column("observed_elapsed_realtime_ms") { it.observation.observedElapsedRealtimeMs },
        Column("observation_time_basis") { it.observation.timeBasis },
        Column("age_at_capture_ms") { it.observation.ageAtCaptureMs },
        Column("freshness_at_capture") { it.observation.freshnessAtCapture },
        Column("fresh_for_ms") { it.observation.freshForMs },
        Column("cached") { it.observation.cached },
        Column("acquisition_status") { it.acquisitionStatus },
        Column("acquisition_detail") { it.acquisitionDetail },
        Column("wifi_bssid") { it.observation.wifi?.bssid },
        Column("wifi_frequency_mhz") { it.observation.wifi?.frequencyMhz?.takeIf { value -> value > 0 } },
        Column("wifi_band") { it.observation.wifi?.band },
        Column("wifi_channel") { it.observation.wifi?.channel?.takeIf { value -> value > 0 } },
        Column("wifi_channel_width") { it.observation.wifi?.channelWidth },
        Column("wifi_standard") { it.observation.wifi?.standard },
        Column("wifi_security") { it.observation.wifi?.security },
        Column("wifi_connected") { it.observation.wifi?.connected },
        Column("bluetooth_address") { it.observation.bluetooth?.address },
        Column("bluetooth_device_type") { it.observation.bluetooth?.deviceType },
        Column("bluetooth_transport") { it.observation.bluetooth?.transport },
        Column("bluetooth_connected") { it.observation.bluetooth?.connected },
        Column("cellular_technology") { it.observation.cellular?.technology },
        Column("cellular_subscription_id") { it.observation.cellular?.subscriptionId },
        Column("cellular_band") { it.observation.cellular?.band },
        Column("cellular_cell_id") { it.observation.cellular?.cellId },
        Column("cellular_registered") { it.observation.cellular?.registered },
        Column("cellular_mcc") { it.observation.cellular?.details?.mcc },
        Column("cellular_mnc") { it.observation.cellular?.details?.mnc },
        Column("cellular_plmn") { it.observation.cellular?.details?.plmn },
        Column("cellular_tac") { it.observation.cellular?.details?.tac },
        Column("cellular_lac") { it.observation.cellular?.details?.lac },
        Column("cellular_ci") { it.observation.cellular?.details?.ci },
        Column("cellular_pci") { it.observation.cellular?.details?.pci },
        Column("cellular_enb") { it.observation.cellular?.details?.enb },
        Column("cellular_eci") { it.observation.cellular?.details?.eci },
        Column("cellular_nci") { it.observation.cellular?.details?.nci },
        Column("cellular_bands") { it.observation.cellular?.details?.bands?.takeIf { list -> list.isNotEmpty() }?.joinToString(";") },
        Column("cellular_arfcn") { it.observation.cellular?.details?.arfcn },
        Column("cellular_uarfcn") { it.observation.cellular?.details?.uarfcn },
        Column("cellular_earfcn") { it.observation.cellular?.details?.earfcn },
        Column("cellular_nr_arfcn") { it.observation.cellular?.details?.nrArfcn },
        Column("cellular_bandwidth_mhz") { it.observation.cellular?.details?.bandwidth },
        Column("cellular_rsrp_dbm") { it.observation.cellular?.details?.rsrp },
        Column("cellular_rsrq_db") { it.observation.cellular?.details?.rsrq },
        Column("cellular_rssnr_raw") { it.observation.cellular?.details?.rssnr },
        Column("cellular_rssnr_db") { it.observation.cellular?.details?.rssnrDb },
        Column("cellular_cqi") { it.observation.cellular?.details?.cqi },
        Column("cellular_timing_advance") { it.observation.cellular?.details?.ta },
        Column("nr_ss_rsrp_dbm") { it.observation.cellular?.details?.ssRsrp },
        Column("nr_ss_rsrq_db") { it.observation.cellular?.details?.ssRsrq },
        Column("nr_ss_sinr_db") { it.observation.cellular?.details?.ssSinr },
        Column("nr_csi_rsrp_dbm") { it.observation.cellular?.details?.csiRsrp },
        Column("nr_csi_rsrq_db") { it.observation.cellular?.details?.csiRsrq },
        Column("nr_csi_sinr_db") { it.observation.cellular?.details?.csiSinr }
    )

    fun writeHeader(writer: Writer) = writeRow(writer, columns.map { it.header })
    fun writeRecord(writer: Writer, row: ObservationRecord) = writeRow(writer, columns.map { it.value(row) })

    private fun writeRow(writer: Writer, values: List<Any?>) {
        writer.write(values.joinToString(",") { value ->
            val text = value?.toString() ?: ""
            if (text.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
                "\"${text.replace("\"", "\"\"")}\"" else text
        })
        writer.write("\r\n")
    }
}
