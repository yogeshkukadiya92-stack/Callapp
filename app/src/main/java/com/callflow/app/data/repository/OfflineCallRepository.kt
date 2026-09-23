package com.callflow.app.data.repository

import androidx.room.withTransaction
import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.DispositionInput
import com.callflow.app.core.model.DispositionOption
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallDispositionEntity
import com.callflow.app.data.local.CallEntity
import com.callflow.app.data.local.CallEventEntity
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.DispositionEntity
import com.callflow.app.data.local.FollowUpEntity
import com.callflow.app.data.local.NoteEntity
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.usecase.DispositionValidation
import com.callflow.app.domain.usecase.DispositionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.callflow.app.notifications.FollowUpReminderScheduler

class OfflineCallRepository @Inject constructor(
    private val database: CallFlowDatabase,
    private val dao: CallFlowDao,
    private val clock: DateTimeProvider,
    private val followUpNotifications: FollowUpReminderScheduler = FollowUpReminderScheduler.NoOp,
) : CallRepository {
    override suspend fun startOutgoingCall(lead: Lead): Result<String> = runCatching {
        val id = UUID.randomUUID().toString()
        val now = clock.now().toEpochMilli()
        val call = CallEntity(id, null, lead.id, "local-user", lead.campaignId, lead.normalizedPhone, CallDirection.OUTGOING.name, now, null, null, null, SyncStatus.PENDING.name)
        val lifecycle = CallEventEntity(UUID.randomUUID().toString(), id, "INITIATED", now)
        dao.insertCallAttemptWithOutbox(call, lifecycle, outbox("CALL", id, "CREATE", "{\"callId\":\"$id\",\"leadId\":\"${lead.id}\"}", now))
        id
    }

    override fun observeCall(callId: String): Flow<CallRecord?> = dao.observeCall(callId).map { it?.toDomain() }
    override fun observeRecentCalls(): Flow<List<CallRecord>> = dao.observeRecentCalls().map { rows -> rows.map(CallEntity::toDomain) }
    override fun observeDispositions(): Flow<List<DispositionOption>> = dao.observeDispositions().map { rows -> rows.map(DispositionEntity::toDomain) }

    override suspend fun addCallNote(callId: String, leadId: String?, body: String): Result<Unit> = runCatching {
        val cleanBody = body.trim()
        require(cleanBody.isNotEmpty()) { "Enter a note or select a tag" }
        require(cleanBody.length <= 500) { "Note must be 500 characters or less" }
        val now = clock.now().toEpochMilli()
        val noteId = UUID.randomUUID().toString()
        if (leadId == null) {
            dao.insertNote(NoteEntity(noteId, null, callId, cleanBody, now, "local-user", "local-install", SyncStatus.PENDING.name))
            return@runCatching
        }
        dao.insertNoteWithOutbox(
            NoteEntity(noteId, leadId, callId, cleanBody, now, "local-user", "local-install", SyncStatus.PENDING.name),
            outbox("NOTE", noteId, "CREATE", org.json.JSONObject().put("leadId", leadId ?: org.json.JSONObject.NULL).put("callId", callId).put("body", cleanBody).put("createdAt", now).toString(), now),
        )
    }

    override suspend fun saveDisposition(input: DispositionInput): Result<Unit> = runCatching {
        val validation = DispositionValidator.validate(input)
        require(validation is DispositionValidation.Valid) { (validation as DispositionValidation.Invalid).message }
        val now = clock.now().toEpochMilli()
        val savedNote = dispositionNote(input)
        val followUpId = input.followUpAt?.let { UUID.randomUUID().toString() }
        database.withTransaction {
            val dispositionId = UUID.randomUUID().toString()
            dao.upsertDispositions(listOf(DispositionEntity(input.disposition.id, input.disposition.code, input.disposition.name, null,
                com.callflow.app.core.model.postCallStatuses.indexOfFirst { it.id == input.disposition.id }.takeIf { it >= 0 } ?: 100,
                true, input.disposition.requiresNote, input.disposition.requiresFollowUp, input.disposition.targetStageId)))
            dao.insertCallDisposition(CallDispositionEntity(dispositionId, input.callId, input.leadId, input.disposition.id, now, "local-user", SyncStatus.PENDING.name))
            val noteId = if (savedNote.isNotBlank()) UUID.randomUUID().toString() else null
            noteId?.let { dao.insertNote(NoteEntity(it, input.leadId, input.callId, savedNote, now, "local-user", "local-install", SyncStatus.PENDING.name)) }
            input.followUpAt?.let { at ->
                val followUpType = when (input.disposition.code) {
                    "GENERATE_MEETING", "MEETING_BOOKED", "MEETING_NO_SHOW" -> "MEETING"
                    "ONLINE_INTRO", "NEXT_TIME_ATTEND" -> "INTRO"
                    "PROPOSAL_SENT" -> "SEND_INFORMATION"
                    "PAYMENT_FOLLOW_UP" -> "PAYMENT_FOLLOW_UP"
                    else -> "CALL"
                }
                dao.insertFollowUp(FollowUpEntity(checkNotNull(followUpId), input.leadId, at.toEpochMilli(), savedNote.ifBlank { null }, 1, "local-user", followUpType, "PENDING", now, now, 1, SyncStatus.PENDING.name))
            }
            dao.updateLeadAfterDisposition(input.leadId, input.disposition.targetStageId ?: input.disposition.code.lowercase(), input.followUpAt?.toEpochMilli(), now, "local-user")
            if (input.disposition.code.equals("WRONG_NUMBER", ignoreCase = true)) dao.markLeadDoNotCall(input.leadId, now, "local-user")
            val safeNote = savedNote.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val safeLink = input.meetingLink.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
            dao.insertSyncEvent(outbox("CALL_DISPOSITION", dispositionId, "CREATE", "{\"leadId\":\"${input.leadId}\",\"callId\":\"${input.callId}\",\"dispositionId\":\"${input.disposition.id}\",\"dispositionCode\":\"${input.disposition.code}\",\"note\":\"$safeNote\",\"meetingLink\":\"$safeLink\",\"createdAt\":$now}", now))
            noteId?.let { dao.insertSyncEvent(outbox("NOTE", it, "CREATE", "{\"leadId\":\"${input.leadId}\",\"callId\":\"${input.callId}\",\"body\":\"$safeNote\",\"createdAt\":$now}", now)) }
            dao.insertSyncEvent(outbox("LEAD", input.leadId, "UPDATE", "{\"stageId\":\"${input.disposition.targetStageId ?: input.disposition.code.lowercase()}\",\"followUpAt\":${input.followUpAt?.toEpochMilli() ?: "null"}}", now))
            followUpId?.let { id ->
                val type = when (input.disposition.code) { "GENERATE_MEETING", "MEETING_BOOKED", "MEETING_NO_SHOW" -> "Meeting"; "PROPOSAL_SENT" -> "Send Information"; "PAYMENT_FOLLOW_UP" -> "Payment Follow-up"; else -> "Call" }
                dao.insertSyncEvent(outbox("FOLLOW_UP", id, "CREATE", "{\"leadId\":\"${input.leadId}\",\"scheduledAt\":${input.followUpAt?.toEpochMilli()},\"type\":\"$type\",\"note\":\"$safeNote\",\"createdAt\":$now}", now))
            }
        }
        followUpId?.let { id -> input.followUpAt?.let { followUpNotifications.schedule(id, it) } }
    }

    suspend fun seedDispositionsIfEmpty() {
        val defaults = listOf(
            option("warm", "WARM", "Warm", 0, false, false, "qualified"),
            option("invite_intro", "INVITE_INTRO", "Invite intro", 1, false, false, "contacted"),
            option("online_intro", "ONLINE_INTRO", "Online intro", 2, false, true, "contacted"),
            option("next_time_attend", "NEXT_TIME_ATTEND", "Next time attend", 3, false, true, "contacted"),
            option("intro_attended", "INTRO_ATTENDED", "Intro attended", 4, false, false, "qualified"),
            option("not_eligible", "NOT_ELIGIBLE", "Not eligible", 5, true, false, "lost"),
            option("generate_meeting", "GENERATE_MEETING", "Generate meeting", 6, false, true, "contacted"),
            option("meeting_booked", "MEETING_BOOKED", "Meeting booked", 7, false, true, "contacted"),
            option("meeting_completed", "MEETING_COMPLETED", "Meeting completed", 8, false, false, "qualified"),
            option("meeting_no_show", "MEETING_NO_SHOW", "Meeting no-show", 9, true, true, "follow_up"),
            option("hot", "HOT", "Hot", 10, false, false, "hot"), option("interested", "INTERESTED", "Interested", 11, false, false, "interested"),
            option("callback", "CALLBACK_REQUESTED", "Callback requested", 12, false, true, "follow_up"), option("follow_up", "FOLLOW_UP", "Follow-up", 13, false, true, "follow_up"), option("info_sent", "INFORMATION_SENT", "Information sent", 14, false, false, "contacted"),
            option("price_shared", "PRICE_SHARED", "Price shared", 15, false, true, "proposal"),
            option("proposal_sent", "PROPOSAL_SENT", "Proposal sent", 16, false, true, "proposal"),
            option("payment_follow_up", "PAYMENT_FOLLOW_UP", "Payment follow-up", 17, false, true, "proposal"),
            option("not_interested", "NOT_INTERESTED", "Not interested", 18, true, false, "lost"), option("no_answer", "NO_ANSWER", "No answer", 19, false, false, null),
            option("busy", "BUSY", "Busy", 20, false, true, null), option("wrong_number", "WRONG_NUMBER", "Wrong number", 21, true, false, "lost"),
            option("converted", "CONVERTED", "Converted", 22, false, false, "won"), option("custom", "CUSTOM", "Custom outcome", 23, true, false, null),
        )
        val existingIds = dao.observeDispositions().first().map(DispositionEntity::id).toSet()
        dao.upsertDispositions(defaults.filterNot { it.id in existingIds })
    }

    private fun dispositionNote(input: DispositionInput): String = buildList {
        input.note.trim().takeIf(String::isNotBlank)?.let(::add)
        input.meetingLink?.trim()?.takeIf(String::isNotBlank)?.let { add("Meeting link: $it") }
    }.joinToString("\n")

    private fun option(id: String, code: String, name: String, order: Int, note: Boolean, followUp: Boolean, stage: String?) = DispositionEntity(id, code, name, null, order, true, note, followUp, stage)
    private fun outbox(type: String, entityId: String, operation: String, payload: String, now: Long): SyncEventEntity {
        val uuid = UUID.randomUUID().toString()
        return SyncEventEntity(uuid, uuid, type, entityId, operation, payload, now, 0, null, SyncStatus.PENDING.name, null)
    }
}

private fun CallEntity.toDomain() = CallRecord(id, leadId, normalizedPhone, CallDirection.valueOf(direction), Instant.ofEpochMilli(startedAt), answeredAt?.let(Instant::ofEpochMilli), endedAt?.let(Instant::ofEpochMilli), failureReason, SyncStatus.valueOf(syncStatus), simSlot, simLabel, phoneAccountId)
private fun DispositionEntity.toDomain() = DispositionOption(id, code, name, requiresNote, requiresFollowUp, targetStageId)
