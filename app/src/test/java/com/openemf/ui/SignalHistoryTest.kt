package com.openemf.ui

import com.openemf.data.fixtureMeasurement
import com.openemf.data.fixtureTime
import com.openemf.data.database.ObservationRecord
import com.openemf.data.repository.observationEntities
import com.openemf.sensors.api.*
import com.openemf.ui.model.*
import org.junit.Assert.*
import org.junit.Test

class SignalHistoryTest {
    private fun row(id: Long = 1): ObservationRecord {
        val m = fixtureMeasurement()
        return ObservationRecord(m.observationEntities().first().copy(rowId = id), fixtureTime,
            CaptureMode.RECORDING, "session", 200000, 42, AcquisitionStatus.CACHED, null, false, MotionState.UNKNOWN)
    }
    private fun series(vararg rows: ObservationRecord, metric: SignalMetric = SignalMetric.RSSI) =
        signalSeries(rows.toList(), metric, fixtureTime.minusSeconds(3600), fixtureTime.plusSeconds(1))

    @Test fun cachedReadUsesObservationTimeAndCollapsesBootClockDuplicates() {
        val original = row()
        val repeated = row(2).copy(capturedAt = fixtureTime.plusSeconds(1),
            observation = original.observation.copy(rowId = 2, observedAt = original.observation.observedAt!!.plusMillis(1)))
        val result = series(original, repeated)
        assertEquals(1, result.points.size)
        assertEquals(1, result.repeatedAppearances)
        assertEquals(repeated.observation.observedAt, result.points.single().time)
        assertNotEquals(repeated.capturedAt, result.points.single().time)
    }

    @Test fun equalBootClockValuesAcrossRebootsRemainSeparate() {
        assertEquals(2, series(row(), row(2).copy(bootCount = 43)).points.size)
    }

    @Test fun unknownBootUsesWallTimeToCollapseRepeatedObservations() {
        val first = row().copy(bootCount = null)
        assertEquals(1, series(first, first.copy(observation = first.observation.copy(rowId = 2))).points.size)
    }

    @Test fun missingStaleUnknownAndOutOfRangeValuesNeverBecomeZeroSamples() {
        val first = row()
        val o = first.observation
        val result = series(first,
            first.copy(observation = o.copy(signalDbm = null)),
            first.copy(observation = o.copy(freshnessAtCapture = Freshness.STALE)),
            first.copy(observation = o.copy(timeBasis = ObservationTimeBasis.UNKNOWN)),
            first.copy(observation = o.copy(observedAt = null)),
            first.copy(observation = o.copy(observedAt = fixtureTime.plusSeconds(10))),
            first.copy(observation = o.copy(observedAt = fixtureTime.minusSeconds(4000))))
        assertEquals(1, result.points.size)
        assertEquals(6, result.excludedAppearances)
        assertEquals(-61.0, result.points.single().value, 0.0)
    }

    @Test fun cellularPowerAndQualityRemainSeparateAndZeroSinrIsValid() {
        val cell = fixtureMeasurement().observationEntities().single { it.sourceType == SourceType.CELLULAR }
        val record = row().copy(observation = cell.copy(freshnessAtCapture = Freshness.FRESH,
            cellular = cell.cellular!!.copy(details = cell.cellular.details!!.copy(ssSinr = 0))))
        assertTrue(series(record).points.isEmpty())
        assertEquals(-95.0, series(record, metric = SignalMetric.SS_RSRP).points.single().value, 0.0)
        assertEquals(-11.0, series(record, metric = SignalMetric.SS_RSRQ).points.single().value, 0.0)
        assertEquals(0.0, series(record, metric = SignalMetric.SS_SINR).points.single().value, 0.0)
        assertEquals("dBm", SignalMetric.SS_RSRP.unit)
        assertEquals("dB", SignalMetric.SS_SINR.unit)
    }

    @Test fun chartPositionsUseElapsedTimeAndAnIncreasingPowerAxis() {
        val bounds = PlotBounds(fixtureTime, fixtureTime.plusSeconds(10), -100.0, -40.0)
        assertEquals(0.1f, bounds.x(fixtureTime.plusSeconds(1)), 0.0001f)
        assertEquals(0.9f, bounds.x(fixtureTime.plusSeconds(9)), 0.0001f)
        assertEquals(0f, bounds.y(-40.0), 0f)
        assertEquals(1f, bounds.y(-100.0), 0f)
        val one = plotBounds(series(row()).points, fixtureTime.minusSeconds(3600), fixtureTime)
        assertTrue(one.high > one.low)
        assertTrue(one.low < -61 && one.high > -61)
    }

    @Test fun fullWidthNrIdentityAndSecondaryMetricsAreVisibleEvenWithoutMainPower() {
        val cell = fixtureMeasurement().observationEntities().single { it.sourceType == SourceType.CELLULAR }.copy(signalDbm = null)
        val fields = cell.technicalFields().toMap()
        assertEquals("68719476735", fields["NCI (36 bit)"])
        assertEquals("17 dB", fields["SS-SINR"])
        assertEquals("-96 dBm", fields["CSI-RSRP"])
        assertEquals("302-001", fields["PLMN"])
        assertEquals("Unavailable", cell.powerText())
        assertEquals("SS-RSRP", cell.metricLabel())
    }

    @Test fun pairedOrDiscoveredBluetoothHasNoInventedPowerOrConnection() {
        val bt = fixtureMeasurement().observationEntities().single { it.sourceType == SourceType.BLUETOOTH }
        assertEquals("Unavailable", bt.powerText())
        assertEquals("Unknown (discovery only)", bt.technicalFields().toMap()["Connection"])
    }
}
