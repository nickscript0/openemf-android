package com.openemf.sensors.api

import android.Manifest

val Permission.androidName: String get() = when (this) {
    Permission.FINE_LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
    Permission.COARSE_LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
    Permission.READ_PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
    Permission.BLUETOOTH_SCAN -> Manifest.permission.BLUETOOTH_SCAN
    Permission.BLUETOOTH_CONNECT -> Manifest.permission.BLUETOOTH_CONNECT
    Permission.NEARBY_WIFI_DEVICES -> Manifest.permission.NEARBY_WIFI_DEVICES
}

/** Scan permissions only; nearby Wi-Fi access is not required by startScan/getScanResults. */
object PermissionPolicy {
    fun required(api: Int): Set<Permission> = buildSet {
        add(Permission.FINE_LOCATION)
        add(Permission.COARSE_LOCATION)
        add(Permission.READ_PHONE_STATE)
        if (api >= 31) {
            add(Permission.BLUETOOTH_SCAN)
            add(Permission.BLUETOOTH_CONNECT)
        }
    }

    fun evaluate(api: Int, granted: Set<Permission>): PermissionStatus {
        val location = Permission.FINE_LOCATION in granted ||
            (api < 29 && Permission.COARSE_LOCATION in granted)
        val bluetooth = if (api >= 31) {
            Permission.BLUETOOTH_SCAN in granted && Permission.BLUETOOTH_CONNECT in granted
        } else Permission.FINE_LOCATION in granted
        val unavailable = buildSet {
            if (!location) add(Modality.WIFI)
            if (!location || Permission.READ_PHONE_STATE !in granted) add(Modality.CELLULAR)
            if (!bluetooth) add(Modality.BLUETOOTH)
        }
        return PermissionStatus(granted, required(api) - granted, unavailable.size < 3, unavailable)
    }
}
