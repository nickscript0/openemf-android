package com.openemf.sensors

import com.openemf.sensors.api.WifiBand
import com.openemf.sensors.wifi.WifiChannels
import org.junit.Assert.*
import org.junit.Test

class WifiChannelsTest {
    @Test fun `six GHz special channel and lower band edge are correct`() {
        mapOf(5935 to 2, 5955 to 1, 5975 to 5, 6115 to 33, 7115 to 233).forEach { (mhz, channel) ->
            assertEquals(channel, WifiChannels.channel(mhz))
            assertEquals(WifiBand.WIFI_6_GHZ, WifiChannels.band(mhz))
        }
    }
    @Test fun `channel fourteen is handled separately from the normal two GHz raster`() {
        mapOf(2412 to 1, 2437 to 6, 2472 to 13, 2484 to 14).forEach { (mhz, channel) ->
            assertEquals(channel, WifiChannels.channel(mhz))
            assertEquals(WifiBand.WIFI_2_4_GHZ, WifiChannels.band(mhz))
        }
    }
    @Test fun `five GHz and public safety raster use their correct offsets`() {
        mapOf(4910 to 182, 5180 to 36, 5500 to 100, 5825 to 165, 5885 to 177).forEach { (mhz, channel) ->
            assertEquals(channel, WifiChannels.channel(mhz))
            assertEquals(WifiBand.WIFI_5_GHZ, WifiChannels.band(mhz))
        }
    }
    @Test fun `unsupported and off raster values do not invent a channel`() {
        listOf(0, -1, 2400, 2483, 3000, 5925, 5940, 5956, 7125, 60000).forEach {
            assertEquals(0, WifiChannels.channel(it))
            assertEquals(WifiBand.UNKNOWN, WifiChannels.band(it))
        }
    }
}
