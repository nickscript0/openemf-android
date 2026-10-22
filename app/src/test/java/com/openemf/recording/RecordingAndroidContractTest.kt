package com.openemf.recording

import android.Manifest
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import com.openemf.sensors.api.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class RecordingAndroidContractTest {
    private lateinit var app: Application
    private lateinit var notifications: RecordingNotifications
    @Before fun setUp() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        notifications = RecordingNotifications(app)
        notifications.ensureChannel()
    }

    @Test fun `manifest declares non-exported location and connected-device service and no boot recorder`() {
        val service = app.packageManager.getServiceInfo(ComponentName(app, RecordingService::class.java), 0)
        assertFalse(service.exported)
        assertEquals(0, service.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, service.foregroundServiceType)
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS)
        val permissions = info.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE))
        assertFalse(permissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        assertFalse(permissions.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
        assertEquals(0, app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun `notification actions are immutable session-scoped and do not retarget old controls`() {
        val one = notifications.build(RecordingState(RecordingPhase.RECORDING, "one", setOf(Modality.WIFI)))
        val two = notifications.build(RecordingState(RecordingPhase.PAUSED, "two", setOf(Modality.WIFI)))
        assertTrue(one.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Pause", "Stop"), one.actions.map { it.title.toString() })
        assertEquals(listOf("Resume", "Stop"), two.actions.map { it.title.toString() })
        assertTrue(one.actions.first().actionIntent.isImmutable)
        two.actions.first().actionIntent.send()
        val newIntent = shadowOf(app).nextStartedService
        assertEquals(RecordingService.RESUME, newIntent.action)
        assertEquals("two", newIntent.getStringExtra(RecordingService.SESSION_ID))
        one.actions.first().actionIntent.send()
        val oldIntent = shadowOf(app).nextStartedService
        assertEquals(RecordingService.PAUSE, oldIntent.action)
        assertEquals("one", oldIntent.getStringExtra(RecordingService.SESSION_ID))
        assertNotEquals(newIntent.data, oldIntent.data)
    }

    @Test fun `channel is visible low importance and notification denial prevents a recording start`() {
        val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(RecordingNotifications.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertTrue(notifications.allowed())
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifications.allowed())
        val store = RecordingStateStore().apply { setAppVisible(true) }
        val controller = controller(store)
        controller.start(60000)
        assertEquals(RecordingPhase.ERROR, store.state.value.phase)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun `only a visible explicit start creates a service request and duplicate starts are ignored`() {
        val store = RecordingStateStore()
        val controller = controller(store)
        controller.start(60000)
        assertNull(shadowOf(app).nextStartedService)
        store.setAppVisible(true)
        controller.start(60000)
        val intent = shadowOf(app).nextStartedService
        assertEquals(RecordingService.START, intent.action)
        assertEquals(store.state.value.sessionId, intent.getStringExtra(RecordingService.SESSION_ID))
        controller.start(60000)
        assertNull(shadowOf(app).nextStartedService)
        controller.stop()
        assertEquals(RecordingPhase.STOPPED, store.state.value.phase)
        assertNull(store.consumeStart(intent.getStringExtra(RecordingService.SESSION_ID)))
    }

    @Test fun `service type mask changes with authorized radios`() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            RecordingNotifications.serviceTypes(setOf(Modality.WIFI, Modality.CELLULAR)))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            RecordingNotifications.serviceTypes(Modality.entries.toSet()))
    }

    @Test fun `dismissing a recording notification requests stop for its own session`() {
        val notification = notifications.build(RecordingState(RecordingPhase.RECORDING, "dismissed", setOf(Modality.WIFI)))
        notification.deleteIntent.send()
        val intent = shadowOf(app).nextStartedService
        assertEquals(RecordingService.STOP, intent.action)
        assertEquals("dismissed", intent.getStringExtra(RecordingService.SESSION_ID))
    }

    @Test fun `wake leases release on close and stale cleanup cannot release a newer lease`() {
        val manager = RecordingWakeLock(app)
        val old = manager.acquire(60000)
        val first = ShadowPowerManager.getLatestWakeLock()
        assertTrue(first.isHeld)
        manager.release()
        assertFalse(first.isHeld)
        val current = manager.acquire(60000)
        val second = ShadowPowerManager.getLatestWakeLock()
        old.close()
        assertTrue(second.isHeld)
        current.close()
        assertFalse(second.isHeld)
        manager.release()
    }

    private fun controller(store: RecordingStateStore): RecordingController {
        val sensors = object : EMFSensorModule {
            override val sensorState = MutableStateFlow<SensorState>(SensorState.Idle)
            override val scanBudget = MutableStateFlow(ScanBudget.initial())
            override val permissionStatus = MutableStateFlow(PermissionPolicy.evaluate(37, PermissionPolicy.required(37)))
            override val monitoringActive = MutableStateFlow(false)
            override val measurements = MutableSharedFlow<Measurement>()
            override suspend fun measureNow(): Result<Measurement> = error("No radio acquisition in a contract test")
            override suspend fun record(sessionId: String, modalities: Set<Modality>): Result<Measurement> = error("No radio acquisition in a contract test")
            override fun startMonitoring(config: MonitoringConfig) = Unit
            override fun stopMonitoring() = Unit
            override fun checkPermissions() = permissionStatus.value
            override fun updateContext(context: MeasurementContext) = Unit
            override fun setForeground(isForeground: Boolean) = Unit
        }
        return RecordingController(app, store, RecordingEnvironment(app, sensors, notifications), notifications)
    }
}
