package com.callflow.app.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.content.ComponentName
import androidx.core.content.ContextCompat
import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.data.local.CallEntity
import com.callflow.app.data.local.CallEventEntity
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.data.session.CallLogCursorStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CallFlowDao,
    private val normalizer: PhoneNumberNormalizer,
    private val cursorStore: CallLogCursorStore,
) {
    suspend fun importNewCalls(limit: Int = 250): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return 0
        val lastImportedAt = cursorStore.lastImportedAt()
        val rows = readRows(lastImportedAt, limit)
        var imported = 0
        var newestSeen = lastImportedAt
        for (row in rows.asReversed()) {
            newestSeen = maxOf(newestSeen, row.startedAt)
            val normalized = normalizer.normalize(row.number) ?: row.number.filter(Char::isDigit)
            if (normalized.isBlank()) continue
            val leads = dao.findByPhone(normalized)
            val leadId = CallMatchResolver.uniqueLeadId(leads.map { it.id })
            val lead = leads.singleOrNull { it.id == leadId }
            val matchingPlatformCall = dao.findMatchingPlatformCall(normalized, row.direction.name, row.startedAt, row.startedAt - 120_000, row.startedAt + 120_000)
            val id = matchingPlatformCall?.id ?: "call-log-${row.logId}"
            val endedAt = row.startedAt + row.durationSeconds * 1000
            val lifecycle = buildList {
                add(CallEventEntity(UUID.randomUUID().toString(), id, if (row.direction == CallDirection.INCOMING) "RINGING" else "INITIATED", row.startedAt))
                if (row.durationSeconds > 0) add(CallEventEntity(UUID.randomUUID().toString(), id, "CONNECTED", row.startedAt))
                val terminal = when {
                    row.missed -> "MISSED"
                    row.durationSeconds == 0L && row.direction == CallDirection.OUTGOING -> "FAILED"
                    else -> "ENDED"
                }
                add(CallEventEntity(UUID.randomUUID().toString(), id, terminal, endedAt))
            }
            val failureReason = when {
                row.missed -> "Incoming call missed"
                row.durationSeconds == 0L && row.direction == CallDirection.OUTGOING -> "Outgoing call not connected"
                else -> null
            }
            val sim = resolveSim(row.phoneAccountId, row.phoneAccountComponent)
            val call = CallEntity(id, null, lead?.id, "local-user", lead?.campaignId, normalized, row.direction.name, row.startedAt, if (row.durationSeconds > 0) row.startedAt else null, endedAt, failureReason, SyncStatus.PENDING.name, sim.slot, sim.label, row.phoneAccountId)
            val outbox = outbox(id, lead?.id, row, normalized, endedAt, sim)
            if (matchingPlatformCall != null) {
                dao.reconcileImportedCallWithOutbox(id, lead?.id, lead?.campaignId, row.startedAt, if (row.durationSeconds > 0) row.startedAt else null, endedAt, failureReason, sim.slot, sim.label, row.phoneAccountId, outbox)
                imported++
            } else if (dao.insertImportedCallWithOutbox(call, lifecycle, outbox)) imported++
        }
        if (newestSeen > lastImportedAt) cursorStore.updateLastImportedAt(newestSeen)
        return imported
    }

    private fun readRows(since: Long, limit: Int): List<Row> {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
        val selection = "${CallLog.Calls.DATE} > ?"
        val rows = mutableListOf<Row>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, arrayOf(since.toString()), "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val accountIdIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
            val accountComponentIndex = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            while (cursor.moveToNext() && rows.size < limit) {
                val type = cursor.getInt(typeIndex)
                val direction = if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE) CallDirection.INCOMING else CallDirection.OUTGOING
                rows += Row(cursor.getLong(idIndex), cursor.getString(numberIndex).orEmpty(), direction, cursor.getLong(dateIndex), cursor.getLong(durationIndex), type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE, accountIdIndex.takeIf { it >= 0 }?.let(cursor::getString), accountComponentIndex.takeIf { it >= 0 }?.let(cursor::getString))
            }
        }
        return rows
    }

    private fun outbox(callId: String, leadId: String?, row: Row, phone: String, endedAt: Long, sim: SimDetails): SyncEventEntity {
        val eventUuid = UUID.randomUUID().toString()
        val leadJson = leadId?.let { "\"$it\"" } ?: "null"
        val simLabel = sim.label?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
        val accountId = row.phoneAccountId?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
        val payload = "{\"callId\":\"$callId\",\"leadId\":$leadJson,\"phone\":\"$phone\",\"direction\":\"${row.direction.name}\",\"createdAt\":${row.startedAt},\"startedAt\":${row.startedAt},\"endedAt\":$endedAt,\"durationSeconds\":${row.durationSeconds},\"simSlot\":${sim.slot ?: "null"},\"simLabel\":$simLabel,\"phoneAccountId\":$accountId,\"source\":\"android_call_log\"}"
        return SyncEventEntity(eventUuid, eventUuid, "CALL", callId, "CREATE", payload, row.startedAt, 0, null, SyncStatus.PENDING.name, null)
    }

    @Suppress("DEPRECATION")
    private fun resolveSim(accountId: String?, component: String?): SimDetails {
        if (accountId.isNullOrBlank()) return SimDetails(null, null)
        var accountLabel: String? = null
        runCatching {
            val componentName = component?.let(ComponentName::unflattenFromString)
            if (componentName != null) accountLabel = context.getSystemService(TelecomManager::class.java)?.getPhoneAccount(PhoneAccountHandle(componentName, accountId))?.label?.toString()
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return SimDetails(null, accountLabel)
        return runCatching {
            val subscriptions = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            val exact = subscriptions.firstOrNull { it.subscriptionId.toString() == accountId }
                ?: subscriptions.firstOrNull { it.iccId?.isNotBlank() == true && it.iccId == accountId }
            val slotFallback = accountId.toIntOrNull()?.takeIf { it in 0..3 }?.let { slot -> subscriptions.firstOrNull { it.simSlotIndex == slot } }
            val selected = exact ?: slotFallback ?: subscriptions.singleOrNull()
            SimDetails(selected?.simSlotIndex?.takeIf { it >= 0 }?.plus(1), selected?.displayName?.toString()?.ifBlank { null } ?: selected?.carrierName?.toString()?.ifBlank { null } ?: accountLabel)
        }.getOrDefault(SimDetails(null, accountLabel))
    }

    private data class SimDetails(val slot: Int?, val label: String?)
    private data class Row(val logId: Long, val number: String, val direction: CallDirection, val startedAt: Long, val durationSeconds: Long, val missed: Boolean, val phoneAccountId: String?, val phoneAccountComponent: String?)
}
