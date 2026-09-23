package com.callflow.app.telecom

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CallLogReconciliationTest {
    @Test
    fun delayedCallLogRowIsImportedWithoutContinuousPolling() = runTest {
        var attempts = 0
        val waits = mutableListOf<Long>()

        val imported = reconcileCallLogAfterResume(
            retryDelayedCallLog = true,
            wait = { waits += it },
        ) {
            attempts += 1
            if (attempts == 2) 1 else 0
        }

        assertEquals(1, imported)
        assertEquals(2, attempts)
        assertEquals(listOf(2_000L), waits)
    }

    @Test
    fun firstAppResumeUsesOnlyOneLightRead() = runTest {
        var attempts = 0

        reconcileCallLogAfterResume(retryDelayedCallLog = false, wait = {}) {
            attempts += 1
            0
        }

        assertEquals(1, attempts)
    }
}
