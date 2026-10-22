package com.openemf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.openemf.sensors.api.EMFSensorModule
import com.openemf.sensors.api.PermissionPolicy
import com.openemf.sensors.api.androidName
import com.openemf.data.preferences.PreferencesRepository
import com.openemf.recording.RecordingController
import com.openemf.recording.RecordingStateStore
import com.openemf.ui.navigation.OpenEMFNavGraph
import com.openemf.ui.theme.OpenEMFTheme
import com.openemf.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry point for the OpenEMF Android app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var sensorModule: EMFSensorModule
    @Inject lateinit var recording: RecordingController
    @Inject lateinit var recordingState: RecordingStateStore
    private var pendingRecordingInterval: Long? = null

    private val recordingNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val interval = pendingRecordingInterval
        pendingRecordingInterval = null
        if (granted && interval != null) recording.start(interval) else if (!granted) recording.notificationDenied()
    }

    private val requiredPermissions: Array<String>
        get() = PermissionPolicy.required(Build.VERSION.SDK_INT).map { it.androidName }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sensorModule.checkPermissions() }

    override fun onStart() {
        super.onStart()
        recordingState.setAppVisible(true)
        sensorModule.setForeground(true)
        sensorModule.checkPermissions()
    }

    override fun onStop() {
        recordingState.setAppVisible(false)
        sensorModule.setForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions on first launch if not granted
        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            OpenEMFTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    OpenEMFNavGraph(
                        navController = navController,
                        onRequestPermissions = { requestPermissions() },
                        onStartRecording = { interval ->
                            if (ContextCompat.checkSelfPermission(this,
                                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                pendingRecordingInterval = interval
                                recordingNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else recording.start(interval)
                        },
                        onOpenUrl = { url -> openUrl(url) }
                    )
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
