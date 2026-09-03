package com.callflow.app.sync

import androidx.room.withTransaction
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.LeadEntity
import com.callflow.app.data.local.SyncConflictEntity
import com.callflow.app.data.local.CallEntity
import com.callflow.app.data.local.CallEventEntity
import com.callflow.app.data.local.NoteEntity
import com.callflow.app.data.local.FollowUpEntity
import com.callflow.app.data.local.LeadStageEntity
import com.callflow.app.data.local.DispositionEntity
import com.callflow.app.data.local.AppConfigurationEntity
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.data.remote.DeltaSyncResponse
import com.callflow.app.data.remote.LeadDeltaDto
import com.callflow.app.data.session.SyncCursorStore
import java.util.UUID
import javax.inject.Inject

class DeltaSyncApplier @Inject constructor(private val database: CallFlowDatabase, private val dao: CallFlowDao, private val cursors: SyncCursorStore, private val clock: DateTimeProvider) {
    suspend fun apply(delta: DeltaSyncResponse) {
        database.withTransaction {
            delta.leads.forEach { remote ->
                val local = dao.getLead(remote.id)
                when (LeadConflictResolver.resolve(local?.version, remote.version, dao.pendingMutationCount("LEAD", remote.id) > 0)) {
                    VersionResolution.ApplyServerValue -> dao.upsertLeads(listOf(remote.toEntity()))
                    VersionResolution.IgnoreStaleServerValue -> Unit
                    VersionResolution.RecordConflict -> dao.insertConflict(SyncConflictEntity(UUID.randomUUID().toString(), "LEAD", remote.id, local?.version ?: 0, remote.version, local?.diagnosticPayload().orEmpty(), remote.diagnosticPayload(), clock.now().toEpochMilli(), "OPEN"))
                }
            }
            delta.deletedLeadIds.forEach { id ->
                val local = dao.getLead(id) ?: return@forEach
                if (dao.pendingMutationCount("LEAD", id) > 0) dao.insertConflict(SyncConflictEntity(UUID.randomUUID().toString(), "LEAD", id, local.version, Long.MAX_VALUE, local.diagnosticPayload(), "{\"deleted\":true}", clock.now().toEpochMilli(), "OPEN"))
                else dao.deleteLead(id)
            }
            // Calls, events, and notes are append-oriented. Existing local rows are never overwritten.
            dao.insertRemoteCalls(delta.calls.map { CallEntity(it.id, it.serverId, it.leadId, it.employeeId, it.campaignId, it.normalizedPhone, it.direction, it.startedAt, it.answeredAt, it.endedAt, it.failureReason, SyncStatus.SYNCED.name, it.simSlot, it.simLabel, it.phoneAccountId) })
            delta.leads.map(LeadDeltaDto::normalizedPhone).distinct().forEach { phone ->
                val matchingLeads = dao.findByPhone(phone).filter { it.serverId != null }
                if (matchingLeads.size == 1) linkUnmatchedCalls(matchingLeads.single())
            }
            dao.insertRemoteCallEvents(delta.callEvents.map { CallEventEntity(it.id, it.callId, it.type, it.occurredAt) })
            dao.insertRemoteNotes(delta.notes.map { NoteEntity(it.id, it.leadId, it.callId, it.body, it.createdAt, it.createdBy, it.deviceId, SyncStatus.SYNCED.name) })
            delta.followUps.forEach { remote ->
                val local = dao.getFollowUp(remote.id)
                when (LeadConflictResolver.resolve(local?.version, remote.version, dao.pendingMutationCount("FOLLOW_UP", remote.id) > 0)) {
                    VersionResolution.ApplyServerValue -> dao.upsertFollowUps(listOf(remote.toEntity()))
                    VersionResolution.IgnoreStaleServerValue -> Unit
                    VersionResolution.RecordConflict -> dao.insertConflict(SyncConflictEntity(UUID.randomUUID().toString(), "FOLLOW_UP", remote.id, local?.version ?: 0, remote.version, local?.diagnosticPayload().orEmpty(), remote.diagnosticPayload(), clock.now().toEpochMilli(), "OPEN"))
                }
            }
            delta.deletedFollowUpIds.forEach { id ->
                val local = dao.getFollowUp(id) ?: return@forEach
                if (dao.pendingMutationCount("FOLLOW_UP", id) > 0) dao.insertConflict(SyncConflictEntity(UUID.randomUUID().toString(), "FOLLOW_UP", id, local.version, Long.MAX_VALUE, local.diagnosticPayload(), "{\"deleted\":true}", clock.now().toEpochMilli(), "OPEN"))
                else dao.deleteFollowUp(id)
            }
            dao.upsertLeadStages(delta.leadStages.map { LeadStageEntity(it.id, it.code, it.name, it.sortOrder, it.active) })
            dao.upsertDispositions(delta.dispositions.map { DispositionEntity(it.id, it.code, it.name, it.icon, it.sortOrder, it.active, it.requiresNote, it.requiresFollowUp, it.targetStageId) })
            dao.upsertAppConfiguration(delta.appConfiguration.map { AppConfigurationEntity(it.key, it.value, it.updatedAt) })
        }
        // Cursor advances only after every database mutation commits.
        cursors.update(delta.nextCursor)
    }

    private suspend fun linkUnmatchedCalls(lead: LeadEntity) {
        dao.unmatchedCallsByPhone(lead.normalizedPhone).forEach { call ->
            dao.linkCallToLead(call.id, lead.id, lead.campaignId)
            val eventId = UUID.randomUUID().toString()
            dao.insertSyncEvent(
                SyncEventEntity(
                    id = eventId,
                    eventUuid = eventId,
                    entityType = "CALL",
                    entityId = call.id,
                    operation = "UPDATE",
                    payload = "{\"callId\":\"${call.id}\",\"leadId\":\"${lead.id}\",\"campaignId\":${lead.campaignId?.let { "\"$it\"" } ?: "null"},\"reason\":\"matched_after_lead_assignment\"}",
                    createdAt = clock.now().toEpochMilli(),
                    attemptCount = 0,
                    lastAttemptAt = null,
                    status = SyncStatus.PENDING.name,
                    lastError = null,
                )
            )
        }
    }
}

private fun LeadDeltaDto.toEntity() = LeadEntity(id, serverId, name, company, city, normalizedPhone, displayPhone, stageId, assignedUserId, campaignId, nextFollowUpAt, updatedAt, updatedBy, version, doNotCall, duplicateCount, score, quality)
private fun com.callflow.app.data.remote.FollowUpDeltaDto.toEntity() = FollowUpEntity(id, leadId, scheduledAt, note, priority, assignedTo, type, status, createdAt, updatedAt, version, SyncStatus.SYNCED.name)
private fun LeadEntity.diagnosticPayload() = "{\"version\":$version,\"stageId\":\"${stageId.replace("\"", "") }\",\"updatedAt\":$updatedAt}"
private fun LeadDeltaDto.diagnosticPayload() = "{\"version\":$version,\"stageId\":\"${stageId.replace("\"", "") }\",\"updatedAt\":$updatedAt}"
private fun FollowUpEntity.diagnosticPayload() = "{\"version\":$version,\"status\":\"${status.replace("\"", "")}\",\"scheduledAt\":$scheduledAt}"
private fun com.callflow.app.data.remote.FollowUpDeltaDto.diagnosticPayload() = "{\"version\":$version,\"status\":\"${status.replace("\"", "")}\",\"scheduledAt\":$scheduledAt}"
