package com.openemf.recording

import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.openemf.sensors.api.EMFSensorModule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensors: EMFSensorModule,
    private val notifications: RecordingNotifications
) {
    fun permissions() = sensors.checkPermissions()
    fun startFailure(): String? = RecordingPolicy.failure(permissions().granted, locationEnabled(), notifications.allowed())
    fun failure(request: RecordingRequest): String? = when {
        !permissions().granted.containsAll(request.requiredPermissions) -> "A recording permission was revoked"
        !locationEnabled() -> "Android Location services were disabled"
        !notifications.allowed() -> "Recording notification was disabled"
        else -> null
    }
    private fun locationEnabled(): Boolean = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
        ?.let { LocationManagerCompat.isLocationEnabled(it) } == true
}
