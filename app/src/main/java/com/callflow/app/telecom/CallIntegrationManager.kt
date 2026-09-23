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
data class CallingAccount(val id: String, val label: String, val slotIndex: Int? = null)

interface CallIntegrationManager {
    fun state(): CallIntegrationState
    fun roleRequestIntent(): Intent?
    fun callingAccounts(): List<CallingAccount>
    fun initiateCall(phoneNumber: String, accountId: String? = null): Outcome<Unit>
}

class SafeDialerCallIntegrationManager @Inject constructor(@ApplicationContext private val context: Context) : CallIntegrationManager {
    override fun state(): CallIntegrationState = CallIntegrationState.Ready
    override fun roleRequestIntent(): Intent? = null
    override fun callingAccounts(): List<CallingAccount> = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return@runCatching emptyList()
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val subscriptionManager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
        val subscriptions = subscriptionManager?.activeSubscriptionInfoList.orEmpty()
        val accounts = telecom?.callCapablePhoneAccounts.orEmpty()
        if (accounts.isNotEmpty()) {
            accounts.mapIndexed { index, handle ->
                val accountLabel = telecom?.getPhoneAccount(handle)?.label?.toString()?.takeIf(String::isNotBlank)
                val sub = subscriptions.firstOrNull { it.subscriptionId.toString() == handle.id }
                    ?: subscriptions.firstOrNull { it.iccId?.isNotBlank() == true && it.iccId == handle.id }
                    ?: subscriptions.firstOrNull { it.simSlotIndex == index }
                val slot = sub?.simSlotIndex?.plus(1) ?: (index + 1)
                val label = sub?.displayName?.toString()?.takeIf(String::isNotBlank)
                    ?: sub?.carrierName?.toString()?.takeIf(String::isNotBlank)
                    ?: accountLabel
                    ?: "SIM $slot"
                CallingAccount(handle.id, label, slot)
            }
        } else if (subscriptions.isNotEmpty()) {
            subscriptions.map { sub ->
                val slot = sub.simSlotIndex + 1
                val label = sub.displayName?.toString()?.takeIf(String::isNotBlank)
                    ?: sub.carrierName?.toString()?.takeIf(String::isNotBlank)
                    ?: "SIM $slot"
                CallingAccount(sub.subscriptionId.toString(), label, slot)
            }
        } else emptyList()
    }.getOrDefault(emptyList())

    override fun initiateCall(phoneNumber: String, accountId: String?): Outcome<Unit> {
        val uri = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        return try {
            val intent = if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!accountId.isNullOrBlank()) {
                        val telecom = context.getSystemService(TelecomManager::class.java)
                        telecom?.callCapablePhoneAccounts?.firstOrNull { it.id == accountId }?.let { handle ->
                            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        }
                    }
                }
            } else {
                Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            Outcome.Success(Unit)
        } catch (error: SecurityException) {
            Outcome.Failure(AppError.PermissionDenied)
        } catch (error: Exception) {
            Outcome.Failure(AppError.Unknown(error))
        }
    }
}
