package com.openemf.data.repository

import com.openemf.data.database.*
import com.openemf.sensors.api.*

internal fun Measurement.observationEntities(): List<SignalObservationEntity> =
    (wifiSources + bluetoothSources + cellularSources).map { source ->
        val time = source.observation
        val captureTime = capturedElapsedRealtimeMs
        SignalObservationEntity(
            measurementId = id, sourceId = source.id, sourceType = source.type, name = source.name,
            signalDbm = source.rssi,
            signalMetric = when ((source as? CellularSource)?.technology) {
                CellularTechnology.LTE_4G -> "RSRP"
                CellularTechnology.NR_5G -> "SS_RSRP"
                null -> "RSSI"
                else -> "PLATFORM_DBM"
            },
            observedAt = time.timestamp, observedElapsedRealtimeMs = time.elapsedRealtimeMs,
            ageAtCaptureMs = captureTime?.let(time::ageMs),
            freshnessAtCapture = captureTime?.let(time::freshness) ?: Freshness.UNKNOWN,
            freshForMs = time.freshForMs, timeBasis = time.timeBasis, cached = time.cached,
            wifi = (source as? WifiSource)?.let {
                WifiObservation(it.bssid, it.frequency.takeIf { value -> value > 0 }, it.band,
                    it.channel.takeIf { value -> value > 0 }, it.channelWidth,
                    it.standard, it.security, if (it.isConnected) true else null)
            },
            bluetooth = (source as? BluetoothSource)?.let {
                BluetoothObservation(it.address, it.deviceType, it.transport, if (it.isConnected) true else null)
            },
            cellular = (source as? CellularSource)?.let {
                CellularObservation(it.technology, it.subscriptionId, it.band, it.cellId, it.isRegistered, it.details)
            }
        )
    }
