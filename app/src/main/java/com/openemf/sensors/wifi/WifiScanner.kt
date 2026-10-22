package com.openemf.sensors.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.openemf.sensors.api.*
import com.openemf.sensors.acquisition.callbackScan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val wifiManager get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @Suppress("DEPRECATION")
    fun isAvailable(): Boolean = wifiManager?.let { it.isWifiEnabled || it.isScanAlwaysAvailable } == true

    fun hasPermissions(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        (Build.VERSION.SDK_INT < 29 && granted(Manifest.permission.ACCESS_COARSE_LOCATION))

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    private fun locationEnabled() = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
        ?.let { LocationManagerCompat.isLocationEnabled(it) } == true

    suspend fun scan(): AcquisitionResult<WifiSource> {
        val manager = wifiManager ?: return result(AcquisitionStatus.UNAVAILABLE, "Wi-Fi hardware unavailable")
        if (!hasPermissions()) return result(AcquisitionStatus.PERMISSION_DENIED, "Precise location permission is required for Wi-Fi scans")
        if (!locationEnabled()) return result(AcquisitionStatus.LOCATION_DISABLED, "Enable Android Location services for Wi-Fi scans")
        if (!isAvailable()) return result(AcquisitionStatus.RADIO_DISABLED, "Wi-Fi and scan-always-available are disabled")
        return try {
            callbackScan<AcquisitionResult<WifiSource>>(12_000) { complete, fail ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                        try {
                            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                            complete(AcquisitionResult(readResults(!updated),
                                if (updated) AcquisitionStatus.UPDATED else AcquisitionStatus.FAILED,
                                if (updated) null else "Android reported scan failure; displaying cached observations"))
                        } catch (e: Exception) { fail(e) }
                    }
                }
                // This protected system broadcast can originate from the privileged Wi-Fi process.
                ContextCompat.registerReceiver(context, receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED)
                try {
                    @Suppress("DEPRECATION")
                    val started = manager.startScan()
                    if (!started) complete(result(AcquisitionStatus.REFUSED,
                        "Android refused the scan (throttling or radio busy)", getCachedResults()))
                } catch (e: Exception) { fail(e) }
                val cleanup: () -> Unit = { runCatching { context.unregisterReceiver(receiver) } }
                cleanup
            } ?: result(AcquisitionStatus.TIMED_OUT, "Wi-Fi scan timed out after 12 seconds", getCachedResults())
        } catch (e: CancellationException) { throw e
        } catch (e: SecurityException) {
            result(AcquisitionStatus.PERMISSION_DENIED, "Wi-Fi permission was denied or revoked")
        } catch (e: Exception) {
            result(AcquisitionStatus.FAILED, e.message ?: "Wi-Fi scan failed", getCachedResults())
        }
    }

    fun getCachedResults(): List<WifiSource> = if (!hasPermissions() || !locationEnabled()) emptyList() else
        try { readResults(true) } catch (_: Exception) { emptyList() }

    private fun readResults(cached: Boolean): List<WifiSource> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            throw SecurityException("Location permission was revoked")
        }
        return wifiManager?.scanResults.orEmpty().map { it.toWifiSource(cached) }
            .distinctBy { it.bssid.lowercase(Locale.ROOT) }
    }

    private fun result(status: AcquisitionStatus, detail: String, sources: List<WifiSource> = emptyList()) =
        AcquisitionResult(sources, status, detail)

    private fun ScanResult.toWifiSource(cached: Boolean): WifiSource {
        val band = WifiChannels.band(frequency)
        return WifiSource(
            id = "wifi:${BSSID.lowercase(Locale.ROOT)}", name = extractSsid(), bssid = BSSID,
            rssi = availableInt(level), frequency = frequency, band = band,
            channel = WifiChannels.channel(frequency), channelWidth = extractChannelWidth(),
            standard = extractWifiStandard(band), security = extractSecurity(),
            isConnected = BSSID.equals(getConnectedBssid(), ignoreCase = true),
            observation = Observation.fromBootTime(timestamp / 1000, SystemClock.elapsedRealtime(), Instant.now(), cached)
        )
    }

    private fun getConnectedBssid(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: Use ConnectivityManager with NetworkCapabilities
                val network = connectivityManager?.activeNetwork ?: return null
                val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return null

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    // Get WifiInfo from TransportInfo
                    val transportInfo = capabilities.transportInfo
                    if (transportInfo is WifiInfo) {
                        transportInfo.bssid
                    } else null
                } else null
            } else {
                // Pre-Android 12: Use deprecated connectionInfo
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo?.bssid
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Extract SSID from ScanResult.
     * Handles API 33+ deprecation where SSID field is deprecated.
     */
    @Suppress("DEPRECATION")
    private fun ScanResult.extractSsid(): String? {
        // On API 33+, wifiSsid returns a WifiSsid object, but SSID still works
        // The SSID field is deprecated but functional; suppress warning
        val ssid = SSID
        return ssid.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    /**
     * Extract channel width from ScanResult.
     */
    private fun ScanResult.extractChannelWidth(): WifiChannelWidth {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> WifiChannelWidth.WIDTH_20_MHZ
                ScanResult.CHANNEL_WIDTH_40MHZ -> WifiChannelWidth.WIDTH_40_MHZ
                ScanResult.CHANNEL_WIDTH_80MHZ -> WifiChannelWidth.WIDTH_80_MHZ
                ScanResult.CHANNEL_WIDTH_160MHZ -> WifiChannelWidth.WIDTH_160_MHZ
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> WifiChannelWidth.WIDTH_80_PLUS_80_MHZ
                else -> {
                    // Check for WiFi 7 320 MHz (API 33+)
                    if (Build.VERSION.SDK_INT >= 33 && channelWidth == 5) {
                        WifiChannelWidth.WIDTH_320_MHZ
                    } else {
                        WifiChannelWidth.UNKNOWN
                    }
                }
            }
        } else {
            WifiChannelWidth.UNKNOWN
        }
    }

    /**
     * Extract WiFi standard from ScanResult.
     * Uses wifiStandard on Android 11+, otherwise infers from capabilities and frequency.
     */
    private fun ScanResult.extractWifiStandard(band: WifiBand): WifiStandard {
        // Android 11+ has direct wifiStandard property
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> WifiStandard.LEGACY
                ScanResult.WIFI_STANDARD_11N -> WifiStandard.WIFI_4
                ScanResult.WIFI_STANDARD_11AC -> WifiStandard.WIFI_5
                ScanResult.WIFI_STANDARD_11AX -> {
                    if (band == WifiBand.WIFI_6_GHZ) WifiStandard.WIFI_6E else WifiStandard.WIFI_6
                }
                else -> {
                    // Check for WiFi 7 (API 34+)
                    if (Build.VERSION.SDK_INT >= 34 && wifiStandard == 8) {
                        WifiStandard.WIFI_7
                    } else {
                        WifiStandard.UNKNOWN
                    }
                }
            }
        }

        // Pre-Android 11: Infer from capabilities string
        val caps = capabilities.uppercase()
        return when {
            band == WifiBand.WIFI_6_GHZ -> WifiStandard.WIFI_6E
            caps.contains("[HE]") || caps.contains("802.11AX") -> WifiStandard.WIFI_6
            caps.contains("[VHT]") || caps.contains("802.11AC") -> WifiStandard.WIFI_5
            caps.contains("[HT]") || caps.contains("802.11N") -> WifiStandard.WIFI_4
            else -> WifiStandard.LEGACY
        }
    }

    /**
     * Extract security type from capabilities string.
     */
    private fun ScanResult.extractSecurity(): WifiSecurity {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("WPA3") && caps.contains("WPA2") -> WifiSecurity.WPA2_WPA3
            caps.contains("WPA3") || caps.contains("SAE") -> WifiSecurity.WPA3
            caps.contains("WPA2") || caps.contains("RSN") -> WifiSecurity.WPA2
            caps.contains("WPA") -> WifiSecurity.WPA
            caps.contains("WEP") -> WifiSecurity.WEP
            caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> WifiSecurity.OPEN
            else -> WifiSecurity.UNKNOWN
        }
    }

}
