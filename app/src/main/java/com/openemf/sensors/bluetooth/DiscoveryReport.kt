package com.openemf.sensors.bluetooth

import com.openemf.sensors.api.AcquisitionReport
import com.openemf.sensors.api.AcquisitionStatus

internal fun discoveryReport(classic: Boolean, ble: Boolean, screenOff: Boolean): AcquisitionReport {
    val details = buildList {
        if (!classic) add("Android refused Classic discovery; BLE results only")
        if (!ble) add("BLE scanner unavailable; Classic results only")
        if (screenOff) add("Unfiltered BLE discovery can be suspended while the screen is off")
        if (isEmpty()) add("BLE and Classic discovery window completed; only discoverable devices can be measured")
    }
    return AcquisitionReport(if (classic && ble && !screenOff) AcquisitionStatus.UPDATED else AcquisitionStatus.PARTIAL,
        details.joinToString(". "))
}
