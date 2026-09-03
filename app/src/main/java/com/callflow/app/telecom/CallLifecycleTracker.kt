package com.callflow.app.telecom

import com.callflow.app.core.model.SyncStatus
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallEntity
import com.callflow.app.data.local.CallEventEntity
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.SyncEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLifecycleTracker @Inject constructor(
    private val dao: CallFlowDao,
    private val clock: DateTimeProvider,
    private val normalizer: PhoneNumberNormalizer,
    private val postCall: PostCallCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class TrackedCall(val id: String, val leadId: String?, val incoming: Boolean)
    private val platformCalls = ConcurrentHashMap<android.telecom.Call, CompletableDeferred<TrackedCall>>()

    fun onCallAdded(call: android.telecom.Call) {
        val resolved = CompletableDeferred<TrackedCall>()
        platformCalls[call] = resolved
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val direction = if (android.os.Build.VERSION.SDK_INT >= 29 && details.callDirection == android.telecom.Call.Details.DIRECTION_INCOMING) "INCOMING" else "OUTGOING"
        val now = clock.now().toEpochMilli()
        scope.launch {
            val normalized = normalizer.normalize(number) ?: number.filter(Char::isDigit)
            val leads = dao.findByPhone(normalized)
            val leadId = CallMatchResolver.uniqueLeadId(leads.map { it.id })
            val lead = leads.singleOrNull { it.id == leadId }
            val openCalls = if (direction == "OUTGOING") dao.findRecentOpenCalls(normalized, now - 120_000) else emptyList()
            val existingId = CallMatchResolver.uniqueOpenCallId(openCalls.map { it.id })
            val id = existingId ?: UUID.randomUUID().toString()
            val lifecycle = CallEventEntity(UUID.randomUUID().toString(), id, if (direction == "INCOMING") "RINGING" else "INITIATED", now)
            if (existingId == null) {
                val outboxId = UUID.randomUUID().toString()
                val entity = CallEntity(id, null, lead?.id, "local-user", lead?.campaignId, normalized, direction, now, null, null, null, SyncStatus.PENDING.name)
                val outbox = SyncEventEntity(outboxId, outboxId, "CALL", id, "CREATE", "{\"callId\":\"$id\",\"leadId\":${lead?.id?.let { "\"$it\"" } ?: "null"}}", now, 0, null, SyncStatus.PENDING.name, null)
                dao.insertCallAttemptWithOutbox(entity, lifecycle, outbox)
            } else {
                dao.insertCallEvent(lifecycle)
            }
            resolved.complete(TrackedCall(id, lead?.id, direction == "INCOMING"))
        }
        call.registerCallback(object : android.telecom.Call.Callback() {
            override fun onStateChanged(value: android.telecom.Call, state: Int) {
                val at = clock.now().toEpochMilli()
                scope.launch {
                    val id = resolved.await().id
                    when (state) {
                        android.telecom.Call.STATE_ACTIVE -> { dao.markCallAnswered(id, at); dao.insertCallEvent(CallEventEntity(UUID.randomUUID().toString(), id, "CONNECTED", at)) }
                        android.telecom.Call.STATE_DISCONNECTED -> { dao.markCallEnded(id, at, value.details.disconnectCause?.label?.toString()); dao.insertCallEvent(CallEventEntity(UUID.randomUUID().toString(), id, "ENDED", at)) }
                        android.telecom.Call.STATE_RINGING -> dao.insertCallEvent(CallEventEntity(UUID.randomUUID().toString(), id, "RINGING", at))
                    }
                }
            }
        })
    }

    fun onCallRemoved(call: android.telecom.Call) {
        val resolved = platformCalls.remove(call) ?: return
        scope.launch {
            val tracked = resolved.await()
            if (tracked.incoming && tracked.leadId != null) postCall.show(tracked.leadId, tracked.id)
        }
    }
}
