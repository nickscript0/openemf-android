package com.openemf.recording

import com.openemf.sensors.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingPhase { IDLE, STARTING, RECORDING, PAUSING, PAUSED, STOPPING, STOPPED, ERROR }

data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val sessionId: String? = null,
    val modalities: Set<Modality> = emptySet(),
    val savedCaptures: Long = 0,
    val lastCaptureAt: Instant? = null,
    val intervalMs: Long = 60_000,
    val message: String? = null
) {
    val active: Boolean get() = phase in setOf(RecordingPhase.STARTING, RecordingPhase.RECORDING,
        RecordingPhase.PAUSING, RecordingPhase.PAUSED, RecordingPhase.STOPPING)
}

data class RecordingRequest(
    val sessionId: String,
    val intervalMs: Long,
    val modalities: Set<Modality>,
    val requiredPermissions: Set<Permission>
)

/** Actual process/service state only. An old Intent or stored preference cannot restart recording. */
@Singleton
class RecordingStateStore @Inject constructor() {
    private val mutable = MutableStateFlow(RecordingState())
    val state = mutable.asStateFlow()
    @Volatile var appVisible: Boolean = false
        private set
    private var pending: RecordingRequest? = null

    fun setAppVisible(visible: Boolean) { appVisible = visible }

    @Synchronized internal fun reserve(request: RecordingRequest): Boolean {
        if (!appVisible || mutable.value.active) return false
        pending = request
        mutable.value = RecordingState(RecordingPhase.STARTING, request.sessionId,
            request.modalities, intervalMs = request.intervalMs)
        return true
    }

    @Synchronized internal fun consumeStart(id: String?): RecordingRequest? {
        val request = pending?.takeIf { it.sessionId == id && appVisible } ?: return null
        pending = null
        return request
    }

    internal fun update(id: String, transform: (RecordingState) -> RecordingState) {
        mutable.update { if (it.sessionId == id) transform(it) else it }
    }

    internal fun reject(message: String) {
        if (!mutable.value.active) mutable.value = RecordingState(phase = RecordingPhase.ERROR, message = message)
    }

    internal fun failStart(id: String, message: String) {
        synchronized(this) { if (pending?.sessionId == id) pending = null }
        update(id) { if (it.phase == RecordingPhase.STARTING) it.copy(phase = RecordingPhase.ERROR, message = message) else it }
    }

    @Synchronized internal fun cancelPending(): Boolean {
        val request = pending ?: return false
        pending = null
        update(request.sessionId) { it.copy(phase = RecordingPhase.STOPPED, message = "Stopped by user") }
        return true
    }

    fun canAcquire(sessionId: String): Boolean = state.value.let {
        it.sessionId == sessionId && it.phase == RecordingPhase.RECORDING
    }
}

/** Recording needs precise scan access; other authorized radios are selected once at Start. */
object RecordingPolicy {
    fun failure(granted: Set<Permission>, locationEnabled: Boolean, notificationsAllowed: Boolean): String? = when {
        Permission.FINE_LOCATION !in granted -> "Allow precise Location access before recording"
        !locationEnabled -> "Enable Android Location services before recording"
        !notificationsAllowed -> "Allow the recording notification so its controls stay available"
        else -> null
    }

    fun request(id: String, intervalMs: Long, permissions: PermissionStatus): RecordingRequest {
        require(intervalMs in 30_000..600_000)
        require(Permission.FINE_LOCATION in permissions.granted)
        val modalities = Modality.entries.toSet() - permissions.degradedModalities
        val required = buildSet {
            add(Permission.FINE_LOCATION)
            if (Modality.BLUETOOTH in modalities) {
                add(Permission.BLUETOOTH_SCAN); add(Permission.BLUETOOTH_CONNECT)
            }
        }
        return RecordingRequest(id, intervalMs, modalities, required)
    }
}
