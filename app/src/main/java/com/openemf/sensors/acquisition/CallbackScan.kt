package com.openemf.sensors.acquisition

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One result, bounded lifetime, cleanup even on synchronous callbacks or cancellation. */
internal suspend fun <T : Any> callbackScan(
    timeoutMs: Long,
    register: (complete: (T) -> Unit, fail: (Throwable) -> Unit) -> (() -> Unit)
): T? = withTimeoutOrNull(timeoutMs) {
    callbackFlow {
        val cleanup = register({ trySend(it); close() }, { close(it) })
        awaitClose { cleanup() }
    }.first()
}
