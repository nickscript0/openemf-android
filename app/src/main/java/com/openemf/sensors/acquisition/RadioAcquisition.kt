package com.openemf.sensors.acquisition

import com.openemf.sensors.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Failure of one radio must not cancel acquisition on the others. Caller cancellation still propagates. */
internal suspend fun <T : Source> acquireRadio(
    timeoutMs: Long,
    scan: suspend () -> AcquisitionResult<T>
): AcquisitionResult<T> = try {
    withTimeoutOrNull(timeoutMs) { scan() }
        ?: AcquisitionResult(emptyList(), AcquisitionStatus.TIMED_OUT, "Radio request exceeded its deadline")
} catch (e: CancellationException) { throw e
} catch (e: SecurityException) {
    AcquisitionResult(emptyList(), AcquisitionStatus.PERMISSION_DENIED, "Permission was denied or revoked")
} catch (e: Exception) {
    AcquisitionResult(emptyList(), AcquisitionStatus.FAILED, e.message ?: "Radio acquisition failed")
}
