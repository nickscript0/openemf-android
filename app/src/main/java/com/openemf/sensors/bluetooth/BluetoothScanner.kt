package com.openemf.sensors.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.openemf.sensors.api.*
import com.openemf.sensors.acquisition.LatestObservations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** BLE advertisements and discoverable Classic devices; pairing never supplies a signal. */
@Singleton
class BluetoothScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
    } else granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun isAvailable(): Boolean = hasPermissions() && runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    suspend fun scan(durationMs: Long = 5_000): AcquisitionResult<BluetoothSource> {
        if (!hasPermissions()) return AcquisitionResult(emptyList(), AcquisitionStatus.PERMISSION_DENIED, "Bluetooth scan/connect permission is required")
        if (Build.VERSION.SDK_INT < 31 && (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
                ?.let { LocationManagerCompat.isLocationEnabled(it) } != true) {
            return AcquisitionResult(emptyList(), AcquisitionStatus.LOCATION_DISABLED, "Enable Android Location services for Bluetooth discovery")
        }
        val radio = adapter ?: return AcquisitionResult(emptyList(), AcquisitionStatus.UNAVAILABLE, "Bluetooth hardware unavailable")
        if (!isAvailable()) return AcquisitionResult(emptyList(), AcquisitionStatus.RADIO_DISABLED, "Bluetooth is disabled")
        val devices = LatestObservations<BluetoothSource>()
        var bleAvailable = false
        var classicStarted = false
        val power = context.getSystemService(PowerManager::class.java)
        var screenOff = power?.isInteractive == false
        return try {
            withTimeoutOrNull(durationMs.coerceIn(1, 15_000)) {
                callbackFlow {
                    var registered = false
                    var bleStarted = false
                    var ownsDiscovery = false
                    val scanner = radio.bluetoothLeScanner
                    val callback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            try { trySend(result.toSource()) } catch (e: SecurityException) { close(e) }
                        }
                        override fun onBatchScanResults(results: List<ScanResult>) {
                            results.forEach { onScanResult(0, it) }
                        }
                        override fun onScanFailed(errorCode: Int) {
                            close(IllegalStateException("BLE scan failed (Android code $errorCode)"))
                        }
                    }
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            if (intent?.action == Intent.ACTION_SCREEN_OFF) { screenOff = true; return }
                            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                            try {
                                @Suppress("DEPRECATION")
                                val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                    else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                if (device != null) {
                                    val dbm = if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt() else null
                                    trySend(source(device, dbm?.takeUnless { it == Short.MIN_VALUE.toInt() }, BluetoothTransport.CLASSIC,
                                        SystemClock.elapsedRealtime(), intent.getStringExtra(BluetoothDevice.EXTRA_NAME)))
                                }
                            } catch (e: SecurityException) { close(e) }
                        }
                    }
                    try {
                        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                            addAction(Intent.ACTION_SCREEN_OFF)
                        }, ContextCompat.RECEIVER_EXPORTED)
                        registered = true
                        if (scanner != null) {
                            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).setReportDelay(0).build(), callback)
                            bleStarted = true
                            bleAvailable = true
                        }
                        // Observe an existing discovery without cancelling another app's request.
                        classicStarted = radio.isDiscovering
                        if (!classicStarted) {
                            ownsDiscovery = radio.startDiscovery()
                            classicStarted = ownsDiscovery
                        }
                        if (!bleStarted && !classicStarted) close(IllegalStateException("Bluetooth discovery could not start"))
                        awaitClose { }
                    } finally {
                        if (bleStarted) runCatching { scanner?.stopScan(callback) }
                        if (ownsDiscovery) runCatching { radio.cancelDiscovery() }
                        if (registered) runCatching { context.unregisterReceiver(receiver) }
                    }
                }.collect { devices.add(it) }
            }
            AcquisitionResult(devices.snapshot(), discoveryReport(classicStarted, bleAvailable,
                screenOff || power?.isInteractive == false))
        } catch (e: CancellationException) { throw e
        } catch (e: SecurityException) {
            AcquisitionResult(emptyList(), AcquisitionStatus.PERMISSION_DENIED, "Bluetooth permission was revoked")
        } catch (e: Exception) {
            AcquisitionResult(devices.snapshot(), AcquisitionStatus.FAILED, "Partial Bluetooth results: ${e.message}")
        }
    }

    fun getPairedDevices(): List<PairedBluetoothDevice> {
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) return emptyList()
        return try { adapter?.bondedDevices.orEmpty().map { PairedBluetoothDevice(it.address, it.name) } }
        catch (_: SecurityException) { emptyList() }
    }

    private fun ScanResult.toSource(): BluetoothSource = source(device,
        rssi.takeUnless { it == 127 || it == Int.MAX_VALUE }, BluetoothTransport.BLE,
        timestampNanos / 1_000_000, scanRecord?.deviceName, ObservationTimeBasis.HARDWARE)

    private fun source(device: BluetoothDevice, dbm: Int?, transport: BluetoothTransport,
                       bootTimeMs: Long, advertisedName: String?,
                       timeBasis: ObservationTimeBasis = ObservationTimeBasis.EVENT_RECEIPT): BluetoothSource {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Bluetooth device information permission was revoked")
        }
        val name = advertisedName ?: device.name
        return BluetoothSource(id = "bluetooth:${device.address.lowercase(Locale.ROOT)}", name = name,
            address = device.address, rssi = dbm, deviceType = classifyDevice(name),
            observation = Observation.fromBootTime(bootTimeMs, SystemClock.elapsedRealtime(), Instant.now(), freshForMs = 30_000, timeBasis = timeBasis),
            transport = transport)
    }

    private fun classifyDevice(name: String?): BluetoothDeviceType {
        val lowerName = name?.lowercase() ?: ""

        return when {
            lowerName.contains("airtag") || lowerName.contains("tile") -> BluetoothDeviceType.AIRTAG
            lowerName.contains("airpod") || lowerName.contains("earbud") -> BluetoothDeviceType.AIRPODS
            lowerName.contains("watch") -> BluetoothDeviceType.SMARTWATCH
            lowerName.contains("band") || lowerName.contains("fitbit") -> BluetoothDeviceType.FITNESS_BAND
            lowerName.contains("speaker") || lowerName.contains("jbl") || lowerName.contains("bose") -> BluetoothDeviceType.SPEAKER
            lowerName.contains("headphone") || lowerName.contains("beats") -> BluetoothDeviceType.HEADPHONES
            lowerName.contains("phone") || lowerName.contains("iphone") || lowerName.contains("galaxy") -> BluetoothDeviceType.PHONE
            lowerName.contains("macbook") || lowerName.contains("laptop") || lowerName.contains("pc") -> BluetoothDeviceType.COMPUTER
            else -> BluetoothDeviceType.UNKNOWN
        }
    }
}
