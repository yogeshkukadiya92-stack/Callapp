package com.callflow.app.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
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
            val id = "call-log-${row.logId}"
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
            val call = CallEntity(id, null, lead?.id, "local-user", lead?.campaignId, normalized, row.direction.name, row.startedAt, if (row.durationSeconds > 0) row.startedAt else null, endedAt, failureReason, SyncStatus.PENDING.name)
            val outbox = outbox(id, lead?.id, row, normalized, endedAt)
            if (dao.insertImportedCallWithOutbox(call, lifecycle, outbox)) imported++
        }
        if (newestSeen > lastImportedAt) cursorStore.updateLastImportedAt(newestSeen)
        return imported
    }

    private fun readRows(since: Long, limit: Int): List<Row> {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val selection = "${CallLog.Calls.DATE} > ?"
        val rows = mutableListOf<Row>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, arrayOf(since.toString()), "${CallLog.Calls.DATE} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && rows.size < limit) {
                val type = cursor.getInt(typeIndex)
                val direction = if (type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE) CallDirection.INCOMING else CallDirection.OUTGOING
                rows += Row(cursor.getLong(idIndex), cursor.getString(numberIndex).orEmpty(), direction, cursor.getLong(dateIndex), cursor.getLong(durationIndex), type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE)
            }
        }
        return rows
    }

    private fun outbox(callId: String, leadId: String?, row: Row, phone: String, endedAt: Long): SyncEventEntity {
        val eventUuid = UUID.randomUUID().toString()
        val leadJson = leadId?.let { "\"$it\"" } ?: "null"
        val payload = "{\"callId\":\"$callId\",\"leadId\":$leadJson,\"phone\":\"$phone\",\"direction\":\"${row.direction.name}\",\"createdAt\":${row.startedAt},\"startedAt\":${row.startedAt},\"endedAt\":$endedAt,\"durationSeconds\":${row.durationSeconds},\"source\":\"android_call_log\"}"
        return SyncEventEntity(eventUuid, eventUuid, "CALL", callId, "CREATE", payload, row.startedAt, 0, null, SyncStatus.PENDING.name, null)
    }

    private data class Row(val logId: Long, val number: String, val direction: CallDirection, val startedAt: Long, val durationSeconds: Long, val missed: Boolean)
}
