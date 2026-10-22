package com.openemf.data

import com.openemf.data.database.ObservationRecord
import com.openemf.data.export.RawObservationCsv
import com.openemf.data.repository.observationEntities
import com.openemf.sensors.api.*
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter
import java.util.Locale

class RawObservationCsvTest {
    private fun rows(measurement: Measurement = fixtureMeasurement()): List<Map<String, String>> {
        val output = StringWriter()
        RawObservationCsv.writeHeader(output)
        measurement.observationEntities().forEachIndexed { index, observation ->
            RawObservationCsv.writeRecord(output, ObservationRecord(observation.copy(rowId = index + 1L),
                measurement.timestamp, measurement.captureMode, measurement.recordingSessionId,
                measurement.capturedElapsedRealtimeMs, measurement.bootCount,
                AcquisitionStatus.CACHED, "Android returned \"old\", cached\r\nreadings",
                measurement.context.isForeground, measurement.context.motionState))
        }
        val parsed = parseCsv(output.toString())
        assertTrue(output.toString().endsWith("\r\n"))
        assertEquals(parsed.first().size, parsed.first().distinct().size)
        return parsed.drop(1).map { row ->
            assertEquals(parsed.first().size, row.size)
            parsed.first().zip(row).toMap()
        }
    }

    @Test fun `names and status details round trip commas quotes unicode CR and LF`() {
        val name = "Maison, café \"☕\"\r\nétage\n2"
        val m = fixtureMeasurement().let { it.copy(wifiSources = listOf(it.wifiSources.single().copy(name = name))) }
        val wifi = rows(m).first { it["source_type"] == "WIFI" }
        assertEquals(name, wifi["source_name"])
        assertEquals("Android returned \"old\", cached\r\nreadings", wifi["acquisition_detail"])
    }

    @Test fun `missing metrics remain empty and pairing and connection are not invented`() {
        val result = rows()
        assertEquals(3, result.size)
        val bluetooth = result.single { it["source_type"] == "BLUETOOTH" }
        for (column in listOf("signal_dbm", "observed_at_utc_estimated", "observed_elapsed_realtime_ms",
            "age_at_capture_ms", "bluetooth_connected", "recording_session_id", "cellular_nci", "wifi_bssid")) {
            assertEquals(column, "", bluetooth[column])
        }
        assertEquals("UNKNOWN", bluetooth["freshness_at_capture"])
        assertEquals("BLE", bluetooth["bluetooth_transport"])
    }

    @Test fun `NR identities metrics units and original PLMN remain distinct`() {
        val row = rows().single { it["source_type"] == "CELLULAR" }
        assertEquals("68719476735", row["cellular_cell_id"])
        assertEquals("68719476735", row["cellular_nci"])
        assertEquals("302001", row["cellular_plmn"])
        assertEquals("77;78", row["cellular_bands"])
        assertEquals("-95", row["nr_ss_rsrp_dbm"])
        assertEquals("-96", row["nr_csi_rsrp_dbm"])
        assertEquals("-11", row["nr_ss_rsrq_db"])
        assertEquals("-12", row["nr_csi_rsrq_db"])
        assertEquals("17", row["nr_ss_sinr_db"])
        assertEquals("16", row["nr_csi_sinr_db"])
        assertEquals("75", row["cellular_rssnr_raw"])
        assertEquals("7.5", row["cellular_rssnr_db"])
        assertEquals("STALE", row["freshness_at_capture"])
    }

    @Test fun `timestamps stay UTC and numeric fields stay locale independent`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val row = rows().first()
            assertEquals("2026-10-22T12:00:00Z", row["captured_at_utc"])
            assertEquals("2026-10-22T11:59:59Z", row["observed_at_utc_estimated"])
            assertEquals("199000", row["observed_elapsed_realtime_ms"])
            assertEquals("1000", row["age_at_capture_ms"])
            assertEquals("true", row["cached"])
            assertEquals("7.5", rows().last()["cellular_rssnr_db"])
        } finally { Locale.setDefault(previous) }
    }

    @Test fun `unknown numeric Wi-Fi channels are empty while valid channel one is retained`() {
        val m = fixtureMeasurement()
        val changed = m.copy(wifiSources = listOf(m.wifiSources.single().copy(channel = 0, frequency = 0)))
        assertEquals("", rows(changed).first()["wifi_channel"])
        assertEquals("", rows(changed).first()["wifi_frequency_mhz"])
        assertEquals("1", rows(m).first()["wifi_channel"])
    }

    /** Reader used as an independent round-trip check, including quoted line breaks. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val char = text[i++]
            when {
                char == '"' && quoted && i < text.length && text[i] == '"' -> { field.append('"'); i++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row.add(field.toString()); field.clear() }
                char == '\r' && !quoted -> {
                    assertEquals('\n', text[i++])
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf()
                }
                else -> field.append(char)
            }
        }
        assertFalse(quoted)
        assertTrue(row.isEmpty())
        return rows
    }
}
