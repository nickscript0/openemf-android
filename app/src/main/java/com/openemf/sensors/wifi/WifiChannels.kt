package com.openemf.sensors.wifi

import com.openemf.sensors.api.WifiBand

/** IEEE center-frequency rasters; unknown/out-of-band inputs stay unknown (channel 0). */
object WifiChannels {
    fun channel(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 && (mhz - 2412) % 5 == 0 -> (mhz - 2407) / 5
        mhz == 5935 -> 2
        mhz in 5955..7115 && (mhz - 5955) % 5 == 0 -> (mhz - 5950) / 5
        mhz in 5160..5885 && mhz % 5 == 0 -> (mhz - 5000) / 5
        mhz in 4910..4980 && mhz % 5 == 0 -> (mhz - 4000) / 5
        else -> 0
    }

    fun band(mhz: Int): WifiBand = when {
        channel(mhz) == 0 -> WifiBand.UNKNOWN
        mhz in 2412..2484 -> WifiBand.WIFI_2_4_GHZ
        mhz == 5935 || mhz in 5955..7115 -> WifiBand.WIFI_6_GHZ
        else -> WifiBand.WIFI_5_GHZ
    }
}
