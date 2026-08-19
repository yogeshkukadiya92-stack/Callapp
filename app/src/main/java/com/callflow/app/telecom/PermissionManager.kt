package com.callflow.app.telecom

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.callflow.app.core.model.PermissionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calls: CallIntegrationManager,
) {
    fun callTrackingRole(): PermissionState = when (calls.state()) {
        CallIntegrationState.Ready -> PermissionState.GRANTED
        CallIntegrationState.RoleRequired -> PermissionState.ROLE_MISSING
        CallIntegrationState.ManualMode -> PermissionState.NOT_REQUIRED
    }

    fun notifications(activity: Activity? = null): PermissionState {
        if (Build.VERSION.SDK_INT < 33) return PermissionState.NOT_REQUIRED
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return PermissionState.GRANTED
        return if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) PermissionState.PERMANENTLY_DENIED else PermissionState.DENIED
    }

    fun callPermission(activity: Activity? = null): PermissionState {
        if (calls.state() != CallIntegrationState.Ready) return PermissionState.NOT_REQUIRED
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) return PermissionState.GRANTED
        return if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CALL_PHONE)) PermissionState.PERMANENTLY_DENIED else PermissionState.DENIED
    }
}
