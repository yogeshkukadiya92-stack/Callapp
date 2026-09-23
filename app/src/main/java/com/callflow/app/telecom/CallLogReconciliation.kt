package com.callflow.app.telecom

import kotlinx.coroutines.delay

internal val CALL_LOG_RETRY_DELAYS_MS = longArrayOf(0L, 2_000L, 6_000L)

/**
 * Rechecks the Android call log after returning from the dialer because some devices publish
 * the completed row asynchronously. The retries are bounded so the app never resumes the old
 * continuous foreground polling behavior.
 */
internal suspend fun reconcileCallLogAfterResume(
    retryDelayedCallLog: Boolean,
    wait: suspend (Long) -> Unit = { delay(it) },
    importNewCalls: suspend () -> Int,
): Int {
    val delays = if (retryDelayedCallLog) CALL_LOG_RETRY_DELAYS_MS else longArrayOf(0L)
    for (delayMs in delays) {
        if (delayMs > 0) wait(delayMs)
        val imported = importNewCalls()
        if (imported > 0) return imported
    }
    return 0
}
