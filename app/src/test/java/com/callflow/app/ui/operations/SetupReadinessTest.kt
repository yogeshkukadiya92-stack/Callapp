package com.callflow.app.ui.operations

import com.callflow.app.core.model.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupReadinessTest {
    private val readyPermissions = PermissionSummary(
        callTracking = PermissionState.GRANTED,
        notifications = PermissionState.GRANTED,
        calling = PermissionState.GRANTED,
        callLog = PermissionState.GRANTED,
    )

    @Test fun fullyConfiguredDeviceIsProductionReady() {
        val result = setupReadiness(readyPermissions, failed = 0, conflicts = 0)
        assertTrue(result.ready)
        assertEquals("CallFlow is production ready", result.title)
        assertEquals(1f, setupProgress(readyPermissions, 0, 0))
    }

    @Test fun missingDefaultPhoneRoleIsBlocking() {
        val permissions = readyPermissions.copy(callTracking = PermissionState.ROLE_MISSING)
        val result = setupReadiness(permissions, failed = 0, conflicts = 0)
        assertFalse(result.ready)
        assertEquals("Default phone setup required", result.title)
    }

    @Test fun failedSyncRecordsReduceReadinessAndShowRetryGuidance() {
        val result = setupReadiness(readyPermissions, failed = 3, conflicts = 0)
        assertFalse(result.ready)
        assertEquals("3 records need sync retry", result.title)
        assertEquals(5f / 6f, setupProgress(readyPermissions, 3, 0))
    }
}
