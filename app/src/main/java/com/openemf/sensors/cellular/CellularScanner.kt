package com.openemf.sensors.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.openemf.sensors.api.*
import com.openemf.sensors.acquisition.callbackScan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded platform refresh requests on the default subscription; no private executor/listener leak. */
@Singleton
class CellularScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun isAvailable(): Boolean = manager != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    fun hasPermissions(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    suspend fun scan(): AcquisitionResult<CellularSource> {
        if (!hasPermissions()) return result(AcquisitionStatus.PERMISSION_DENIED, "Precise location permission is required for cell information")
        val tm = manager ?: return result(AcquisitionStatus.UNAVAILABLE, "Cellular hardware unavailable")
        if (!isAvailable()) return result(AcquisitionStatus.UNAVAILABLE, "Cellular hardware unavailable")
        if (Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
            return result(AcquisitionStatus.RADIO_DISABLED, "Cellular radio is disabled by airplane mode")
        if ((context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
                ?.let { LocationManagerCompat.isLocationEnabled(it) } != true)
            return result(AcquisitionStatus.LOCATION_DISABLED, "Enable Android Location services for cell information")
        if (Build.VERSION.SDK_INT < 29) return result(AcquisitionStatus.CACHED,
            "This Android version provides cached cell info only", getCachedResults())
        return try {
            callbackScan<AcquisitionResult<CellularSource>>(8_000) { complete, fail ->
                val callback = object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        try { complete(AcquisitionResult(convert(cellInfo, false), AcquisitionStatus.UPDATED)) }
                        catch (e: Exception) { fail(e) }
                    }
                    override fun onError(errorCode: Int, detail: Throwable?) {
                        fail(IllegalStateException("Cell information update failed (Android code $errorCode)", detail))
                    }
                }
                tm.requestCellInfoUpdate(ContextCompat.getMainExecutor(context), callback)
                // Android exposes no cancel method for this one-shot request. Late results are ignored.
                val cleanup: () -> Unit = { }
                cleanup
            } ?: result(AcquisitionStatus.TIMED_OUT, "Cell update timed out after 8 seconds; cached observations retained", getCachedResults())
        } catch (e: CancellationException) { throw e
        } catch (e: SecurityException) {
            result(AcquisitionStatus.PERMISSION_DENIED, "Cellular permission was denied or revoked")
        } catch (e: UnsupportedOperationException) {
            result(AcquisitionStatus.UNAVAILABLE, "Cell information refresh is not supported", getCachedResults())
        } catch (e: Exception) {
            result(AcquisitionStatus.FAILED, e.message ?: "Cell update failed", getCachedResults())
        }
    }

    fun getCachedResults(): List<CellularSource> = if (!hasPermissions()) emptyList() else
        try { convert(manager?.allCellInfo.orEmpty(), true) }
        catch (_: SecurityException) { emptyList() } catch (_: Exception) { emptyList() }

    private fun result(status: AcquisitionStatus, detail: String, sources: List<CellularSource> = emptyList()) =
        AcquisitionResult(sources, status, detail)

    private fun convert(cells: List<CellInfo>, cached: Boolean): List<CellularSource> = cells.mapNotNull { cell ->
        val observation = Observation.fromBootTime(
            if (Build.VERSION.SDK_INT >= 30) cell.timestampMillis else @Suppress("DEPRECATION") (cell.timeStamp / 1_000_000),
            SystemClock.elapsedRealtime(), Instant.now(), cached)
        when (cell) {
            is CellInfoLte -> lte(cell, observation)
            is CellInfoGsm -> gsm(cell, observation)
            is CellInfoWcdma -> wcdma(cell, observation)
            else -> if (Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr) nr(cell, observation) else null
        }
    }.distinctBy { it.id }

    private fun source(cell: CellInfo, technology: CellularTechnology, dbm: Int?, details: CellularDetails,
                       observation: Observation, operator: String? = null): CellularSource {
        val subscription = if (Build.VERSION.SDK_INT >= 30) manager?.subscriptionId?.takeIf { it >= 0 } else null
        val identity = if (technology == CellularTechnology.NR_5G) details.nci else details.ci?.toLong()
        val channel = details.nrArfcn ?: details.earfcn ?: details.uarfcn ?: details.arfcn
        return CellularSource(
            id = cellularIdentity(technology, subscription, details.plmn ?: details.formattedPlmn,
                details.tac ?: details.lac, identity, details.pci, channel),
            name = operator?.takeIf { it.isNotBlank() } ?: details.formattedPlmn,
            rssi = dbm, technology = technology, band = details.bands.singleOrNull(),
            cellId = identity, isRegistered = cell.isRegistered, details = details,
            observation = observation, subscriptionId = subscription)
    }

    @Suppress("DEPRECATION")
    private fun lte(cell: CellInfoLte, observation: Observation): CellularSource {
        val id = cell.cellIdentity
        val signal = cell.cellSignalStrength
        val mcc = if (Build.VERSION.SDK_INT >= 28) id.mccString else null
        val mnc = if (Build.VERSION.SDK_INT >= 28) id.mncString else null
        val ci = availableInt(id.ci)
        val details = CellularDetails(
            mcc = mcc?.toIntOrNull() ?: availableInt(id.mcc), mnc = mnc?.toIntOrNull() ?: availableInt(id.mnc),
            plmn = if (mcc != null && mnc != null) mcc + mnc else null,
            tac = availableInt(id.tac), ci = ci, pci = availableInt(id.pci),
            enb = ci?.div(256), eci = ci?.toLong(),
            rsrp = availableInt(signal.rsrp), rsrq = availableInt(signal.rsrq),
            rssnr = availableInt(signal.rssnr), rssnrDb = lteRssnrDb(signal.rssnr, Build.VERSION.SDK_INT),
            cqi = availableInt(signal.cqi), ta = availableInt(signal.timingAdvance),
            earfcn = availableInt(id.earfcn),
            bandwidth = if (Build.VERSION.SDK_INT >= 28) availableInt(id.bandwidth)?.div(1000) else null,
            bands = if (Build.VERSION.SDK_INT >= 30) id.bands.toList() else emptyList())
        return source(cell, CellularTechnology.LTE_4G, availableInt(signal.dbm), details, observation,
            if (Build.VERSION.SDK_INT >= 28) id.operatorAlphaLong?.toString() else null)
    }

    @Suppress("DEPRECATION")
    private fun gsm(cell: CellInfoGsm, observation: Observation): CellularSource {
        val id = cell.cellIdentity
        val mcc = if (Build.VERSION.SDK_INT >= 28) id.mccString else null
        val mnc = if (Build.VERSION.SDK_INT >= 28) id.mncString else null
        val details = CellularDetails(
            mcc = mcc?.toIntOrNull() ?: availableInt(id.mcc), mnc = mnc?.toIntOrNull() ?: availableInt(id.mnc),
            plmn = if (mcc != null && mnc != null) mcc + mnc else null,
            ci = availableInt(id.cid), lac = availableInt(id.lac), arfcn = availableInt(id.arfcn))
        return source(cell, CellularTechnology.GSM_2G, availableInt(cell.cellSignalStrength.dbm), details, observation,
            if (Build.VERSION.SDK_INT >= 28) id.operatorAlphaLong?.toString() else null)
    }

    @Suppress("DEPRECATION")
    private fun wcdma(cell: CellInfoWcdma, observation: Observation): CellularSource {
        val id = cell.cellIdentity
        val mcc = if (Build.VERSION.SDK_INT >= 28) id.mccString else null
        val mnc = if (Build.VERSION.SDK_INT >= 28) id.mncString else null
        val details = CellularDetails(
            mcc = mcc?.toIntOrNull() ?: availableInt(id.mcc), mnc = mnc?.toIntOrNull() ?: availableInt(id.mnc),
            plmn = if (mcc != null && mnc != null) mcc + mnc else null,
            ci = availableInt(id.cid), lac = availableInt(id.lac), uarfcn = availableInt(id.uarfcn), pci = availableInt(id.psc))
        return source(cell, CellularTechnology.UMTS_3G, availableInt(cell.cellSignalStrength.dbm), details, observation,
            if (Build.VERSION.SDK_INT >= 28) id.operatorAlphaLong?.toString() else null)
    }

    @RequiresApi(29)
    private fun nr(cell: CellInfoNr, observation: Observation): CellularSource {
        val id = cell.cellIdentity as CellIdentityNr
        val signal = cell.cellSignalStrength as CellSignalStrengthNr
        val mcc = id.mccString
        val mnc = id.mncString
        val details = CellularDetails(
            mcc = mcc?.toIntOrNull(), mnc = mnc?.toIntOrNull(),
            plmn = if (mcc != null && mnc != null) mcc + mnc else null,
            tac = availableInt(id.tac), pci = availableInt(id.pci), nci = availableNci(id.nci), nrArfcn = availableInt(id.nrarfcn),
            bands = if (Build.VERSION.SDK_INT >= 30) id.bands.toList() else emptyList(),
            ssRsrp = availableInt(signal.ssRsrp), ssRsrq = availableInt(signal.ssRsrq), ssSinr = availableInt(signal.ssSinr),
            csiRsrp = availableInt(signal.csiRsrp), csiRsrq = availableInt(signal.csiRsrq), csiSinr = availableInt(signal.csiSinr))
        return source(cell, CellularTechnology.NR_5G, availableInt(signal.dbm), details, observation, id.operatorAlphaLong?.toString())
    }
}
