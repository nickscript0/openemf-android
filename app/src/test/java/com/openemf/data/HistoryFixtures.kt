package com.openemf.data

import com.openemf.sensors.api.*
import java.time.Instant

internal val fixtureTime: Instant = Instant.parse("2026-10-22T12:00:00Z")
internal fun fixtureMeasurement(id: String = "capture", score: Int = 40): Measurement {
    val time = Observation.fromBootTime(199_000, 200_000, fixtureTime, cached = true)
    val wifi = WifiSource("wifi:aa", "Home", "aa", -61, 5955, WifiBand.WIFI_6_GHZ, 1,
        channelWidth = WifiChannelWidth.WIDTH_160_MHZ, standard = WifiStandard.WIFI_6E,
        security = WifiSecurity.WPA3, observation = time)
    val bluetooth = BluetoothSource("bt:bb", "Speaker", "bb", null, BluetoothDeviceType.SPEAKER,
        observation = Observation(), transport = BluetoothTransport.BLE)
    val cell = CellularSource("cell:nr", "Carrier", -95, CellularTechnology.NR_5G, 78,
        68_719_476_735L, true, details = CellularDetails(
            mcc = 302, mnc = 1, plmn = "302001", nci = 68_719_476_735L, nrArfcn = 640000,
            tac = 12345, pci = 901, bands = listOf(77, 78), ssRsrp = -95, ssRsrq = -11,
            ssSinr = 17, csiRsrp = -96, csiRsrq = -12, csiSinr = 16,
            rssnr = 75, rssnrDb = 7.5, cqi = 8, ta = 12,
            lac = 23, arfcn = 24, uarfcn = 25, earfcn = 26, bandwidth = 20,
            ci = 123, eci = 123, enb = 0, rsrp = -100, rsrq = -15),
        observation = time.copy(elapsedRealtimeMs = 1, timestamp = fixtureTime.minusMillis(199_999)), subscriptionId = 7)
    return Measurement(id, fixtureTime, EScoreResult(score, ExposureLevel.MODERATE, -60, 0.000001,
        mapOf(SourceType.WIFI to 60, SourceType.CELLULAR to 40), wifi, 2), listOf(wifi), listOf(bluetooth), listOf(cell),
        null, MeasurementContext(), ConfidenceFlags(1f, 0f, 1f, 0.67f),
        acquisition = mapOf(Modality.WIFI to AcquisitionReport(AcquisitionStatus.CACHED, "Cached result"),
            Modality.BLUETOOTH to AcquisitionReport(AcquisitionStatus.PARTIAL),
            Modality.CELLULAR to AcquisitionReport(AcquisitionStatus.UPDATED)),
        pairedBluetoothDevices = listOf(PairedBluetoothDevice("paired-only", "Keyboard")),
        capturedElapsedRealtimeMs = 200_000, bootCount = 42)
}
