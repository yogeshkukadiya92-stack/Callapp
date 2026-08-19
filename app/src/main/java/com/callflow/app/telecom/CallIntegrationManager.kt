package com.callflow.app.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.callflow.app.core.model.Outcome
import com.callflow.app.core.model.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface CallIntegrationState { data object ManualMode : CallIntegrationState; data object RoleRequired : CallIntegrationState; data object Ready : CallIntegrationState }

interface CallIntegrationManager {
    fun state(): CallIntegrationState
    fun roleRequestIntent(): Intent?
    fun initiateCall(phoneNumber: String): Outcome<Unit>
}

class SafeDialerCallIntegrationManager @Inject constructor(@ApplicationContext private val context: Context) : CallIntegrationManager {
    override fun state(): CallIntegrationState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return CallIntegrationState.ManualMode
        val roles = context.getSystemService(RoleManager::class.java)
        return if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) CallIntegrationState.ManualMode else if (roles.isRoleHeld(RoleManager.ROLE_DIALER)) CallIntegrationState.Ready else CallIntegrationState.RoleRequired
    }
    override fun roleRequestIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).takeIf { it.isRoleAvailable(RoleManager.ROLE_DIALER) }?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else null
    override fun initiateCall(phoneNumber: String): Outcome<Unit> {
        val uri = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        if (state() == CallIntegrationState.Ready && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return Outcome.Failure(AppError.PermissionDenied)
        return try {
            if (state() == CallIntegrationState.Ready) context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
            else context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Outcome.Success(Unit)
        } catch (error: SecurityException) {
            Outcome.Failure(AppError.PermissionDenied)
        } catch (error: Exception) {
            Outcome.Failure(AppError.Unknown(error))
        }
    }
}
