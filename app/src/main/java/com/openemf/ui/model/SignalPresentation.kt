package com.openemf.ui.model

import com.openemf.data.database.SignalObservationEntity
import com.openemf.sensors.api.SourceType

fun SourceType.label(): String = when (this) {
    SourceType.WIFI -> "Wi-Fi"
    SourceType.BLUETOOTH -> "Bluetooth"
    SourceType.CELLULAR -> "Cellular"
}

fun String.readable(): String = lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
fun SignalObservationEntity.powerText(): String = signalDbm?.let { "$it dBm" } ?: "Unavailable"
fun SignalObservationEntity.metricLabel(): String = signalMetric.replace('_', '-')

fun SignalObservationEntity.technicalFields(): List<Pair<String, String>> = buildList {
    fun field(label: String, value: Any?, unit: String = "") {
        add(label to (value?.let { "$it$unit" } ?: "Unavailable"))
    }
    wifi?.let { w ->
        field("BSSID", w.bssid)
        field("Frequency", w.frequencyMhz, " MHz")
        field("Channel", w.channel)
        field("Channel width", when (w.channelWidth.name) {
            "UNKNOWN" -> null
            "WIDTH_80_PLUS_80_MHZ" -> "80 + 80 MHz"
            else -> w.channelWidth.name.removePrefix("WIDTH_").replace("_MHZ", " MHz")
        })
        field("Standard", w.standard.name.readable().replace("Wifi ", "Wi-Fi "))
        field("Security", w.security.name.replace('_', '/'))
        field("Connection", if (w.connected == true) "Connected" else "Unknown")
    }
    bluetooth?.let { b ->
        field("Address", b.address)
        field("Transport", b.transport.name)
        field("Device category", b.deviceType.name.readable())
        field("Connection", if (b.connected == true) "Connected" else "Unknown (discovery only)")
    }
    cellular?.let { c ->
        field("Technology", c.technology.name.replace('_', ' '))
        field("Cell identity", c.cellId)
        field("Subscription", c.subscriptionId)
        field("Registration", if (c.registered) "Serving cell" else "Not registered")
        field("Band", c.band)
        c.details?.let { d ->
            field("PLMN", d.formattedPlmn)
            field("Bands", d.bands.takeIf { it.isNotEmpty() }?.joinToString())
            field("TAC", d.tac); field("LAC", d.lac); field("PCI", d.pci)
            field("ECI", d.eci); field("NCI (36 bit)", d.nci)
            field("EARFCN", d.earfcn); field("NR-ARFCN", d.nrArfcn)
            field("ARFCN", d.arfcn); field("UARFCN", d.uarfcn)
            field("Bandwidth", d.bandwidth, " MHz")
            field("RSRP", d.rsrp, " dBm"); field("RSRQ", d.rsrq, " dB")
            field("RSSNR", d.rssnrDb, " dB"); field("RSSNR (raw Android)", d.rssnr)
            field("SS-RSRP", d.ssRsrp, " dBm"); field("SS-RSRQ", d.ssRsrq, " dB")
            field("SS-SINR", d.ssSinr, " dB")
            field("CSI-RSRP", d.csiRsrp, " dBm"); field("CSI-RSRQ", d.csiRsrq, " dB")
            field("CSI-SINR", d.csiSinr, " dB"); field("CQI", d.cqi); field("Timing advance (raw Android)", d.ta)
        }
    }
}
