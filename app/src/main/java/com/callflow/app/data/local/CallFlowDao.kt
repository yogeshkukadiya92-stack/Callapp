package com.callflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CallFlowDao {
    @Query("SELECT * FROM leads ORDER BY COALESCE(nextFollowUpAt, 9223372036854775807), updatedAt DESC LIMIT :limit")
    fun observeCallingQueue(limit: Int = 50): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    fun observeAllLeads(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE name LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%' OR city LIKE '%' || :query || '%' OR (:digits != '' AND normalizedPhone LIKE '%' || :digits || '%') ORDER BY updatedAt DESC LIMIT :limit")
    fun searchLeads(query: String, digits: String, limit: Int = 100): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE normalizedPhone = :normalizedPhone")
    suspend fun findByPhone(normalizedPhone: String): List<LeadEntity>

    @Query("SELECT * FROM leads WHERE id = :id LIMIT 1")
    suspend fun getLead(id: String): LeadEntity?

    @Query("SELECT * FROM leads WHERE id = :id LIMIT 1")
    fun observeLead(id: String): Flow<LeadEntity?>

    @Query("SELECT * FROM notes WHERE leadId = :leadId ORDER BY createdAt DESC")
    fun observeNotes(leadId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE callId = :callId ORDER BY createdAt DESC")
    fun observeCallNotes(callId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM calls WHERE leadId = :leadId ORDER BY startedAt DESC")
    fun observeCalls(leadId: String): Flow<List<CallEntity>>

    @Query("""SELECT leadId, COUNT(*) AS attempts,
        COALESCE(SUM(CASE WHEN answeredAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS connected,
        COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND answeredAt IS NULL THEN 1 ELSE 0 END), 0) AS missed,
        COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND answeredAt IS NULL THEN 1 ELSE 0 END), 0) AS notConnected,
        COALESCE(SUM(CASE WHEN answeredAt IS NOT NULL AND endedAt IS NOT NULL THEN MAX(0, (endedAt - answeredAt) / 1000) ELSE 0 END), 0) AS talkTimeSeconds,
        MIN(startedAt) AS firstContactedAt, MAX(startedAt) AS lastContactedAt
        FROM calls WHERE leadId IS NOT NULL GROUP BY leadId""")
    fun observeLeadCallSummaries(): Flow<List<LeadCallSummary>>

    @Query("SELECT * FROM follow_ups WHERE leadId = :leadId ORDER BY scheduledAt DESC")
    fun observeFollowUps(leadId: String): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeads(leads: List<LeadEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLead(lead: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCall(call: CallEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedCall(call: CallEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteCalls(calls: List<CallEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCallEvent(event: CallEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteCallEvents(events: List<CallEventEntity>)

    @Query("UPDATE calls SET answeredAt = COALESCE(answeredAt, :at) WHERE id = :callId")
    suspend fun markCallAnswered(callId: String, at: Long)

    @Query("UPDATE calls SET endedAt = :at, failureReason = :failureReason WHERE id = :callId")
    suspend fun markCallEnded(callId: String, at: Long, failureReason: String? = null)

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    fun observeCall(id: String): Flow<CallEntity?>

    @Query("SELECT * FROM calls ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentCalls(limit: Int = 250): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE normalizedPhone = :phone AND direction = 'OUTGOING' AND endedAt IS NULL AND startedAt >= :since ORDER BY startedAt DESC LIMIT 2")
    suspend fun findRecentOpenCalls(phone: String, since: Long): List<CallEntity>

    @Query("SELECT * FROM calls WHERE leadId IS NULL AND normalizedPhone = :phone ORDER BY startedAt")
    suspend fun unmatchedCallsByPhone(phone: String): List<CallEntity>

    @Query("UPDATE calls SET leadId = :leadId, campaignId = :campaignId, syncStatus = 'PENDING' WHERE id = :callId AND leadId IS NULL")
    suspend fun linkCallToLead(callId: String, leadId: String, campaignId: String?)

    @Query("SELECT * FROM calls WHERE normalizedPhone = :phone AND direction = :direction AND startedAt BETWEEN :from AND :to ORDER BY ABS(startedAt - :startedAt) LIMIT 1")
    suspend fun findMatchingPlatformCall(phone: String, direction: String, startedAt: Long, from: Long, to: Long): CallEntity?

    @Query("UPDATE calls SET leadId = COALESCE(leadId, :leadId), campaignId = COALESCE(campaignId, :campaignId), startedAt = :startedAt, answeredAt = :answeredAt, endedAt = :endedAt, failureReason = :failureReason, simSlot = :simSlot, simLabel = :simLabel, phoneAccountId = :phoneAccountId, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun reconcileCallFromSystemLog(id: String, leadId: String?, campaignId: String?, startedAt: Long, answeredAt: Long?, endedAt: Long, failureReason: String?, simSlot: Int?, simLabel: String?, phoneAccountId: String?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFollowUp(followUp: FollowUpEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowUps(values: List<FollowUpEntity>)

    @Query("SELECT * FROM follow_ups WHERE id = :id LIMIT 1")
    suspend fun getFollowUp(id: String): FollowUpEntity?

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun deleteFollowUp(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCallDisposition(disposition: CallDispositionEntity)

    @Query("UPDATE leads SET stageId = :stageId, nextFollowUpAt = :followUpAt, updatedAt = :updatedAt, updatedBy = :updatedBy, version = version + 1 WHERE id = :leadId")
    suspend fun updateLeadAfterDisposition(leadId: String, stageId: String, followUpAt: Long?, updatedAt: Long, updatedBy: String)

    @Query("UPDATE leads SET doNotCall = 1, updatedAt = :updatedAt, updatedBy = :updatedBy, version = version + 1 WHERE id = :leadId")
    suspend fun markLeadDoNotCall(leadId: String, updatedAt: Long, updatedBy: String)

    @Query("SELECT * FROM dispositions WHERE active = 1 ORDER BY sortOrder")
    fun observeDispositions(): Flow<List<DispositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDispositions(values: List<DispositionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeadStages(values: List<LeadStageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppConfiguration(values: List<AppConfigurationEntity>)

    @Query("SELECT * FROM app_configuration ORDER BY updatedAt DESC")
    fun observeAppConfiguration(): Flow<List<AppConfigurationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncEvent(event: SyncEventEntity)

    @Transaction
    suspend fun insertCallAttemptWithOutbox(call: CallEntity, lifecycle: CallEventEntity, event: SyncEventEntity) {
        insertCall(call)
        insertCallEvent(lifecycle)
        insertSyncEvent(event)
    }

    @Transaction
    suspend fun insertImportedCallWithOutbox(call: CallEntity, lifecycle: List<CallEventEntity>, event: SyncEventEntity): Boolean {
        if (insertImportedCall(call) == -1L) return false
        lifecycle.forEach { insertCallEvent(it) }
        insertSyncEvent(event)
        return true
    }

    @Transaction
    suspend fun reconcileImportedCallWithOutbox(id: String, leadId: String?, campaignId: String?, startedAt: Long, answeredAt: Long?, endedAt: Long, failureReason: String?, simSlot: Int?, simLabel: String?, phoneAccountId: String?, event: SyncEventEntity) {
        reconcileCallFromSystemLog(id, leadId, campaignId, startedAt, answeredAt, endedAt, failureReason, simSlot, simLabel, phoneAccountId)
        insertSyncEvent(event)
    }

    @Transaction
    suspend fun insertNoteWithOutbox(note: NoteEntity, event: SyncEventEntity) {
        insertNote(note)
        insertSyncEvent(event)
    }

    @Query("SELECT * FROM call_events WHERE callId = :callId ORDER BY occurredAt")
    suspend fun callEvents(callId: String): List<CallEventEntity>

    @Query("SELECT * FROM sync_events WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt")
    suspend fun syncEvents(entityType: String, entityId: String): List<SyncEventEntity>

    @Query("SELECT * FROM sync_events WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt LIMIT :limit")
    suspend fun pendingSyncEvents(limit: Int): List<SyncEventEntity>

    @Query("UPDATE sync_events SET status = 'SYNCING', attemptCount = attemptCount + 1, lastAttemptAt = :at, lastError = NULL WHERE id IN (:ids) AND status IN ('PENDING', 'FAILED')")
    suspend fun markSyncing(ids: List<String>, at: Long)

    @Query("UPDATE sync_events SET status = 'SYNCED', lastError = NULL WHERE eventUuid IN (:eventUuids)")
    suspend fun markSynced(eventUuids: List<String>)

    @Query("UPDATE sync_events SET status = 'FAILED', lastError = :error WHERE eventUuid IN (:eventUuids) AND status != 'SYNCED'")
    suspend fun markSyncFailed(eventUuids: List<String>, error: String)

    @Query("SELECT COUNT(*) FROM sync_events WHERE entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun pendingMutationCount(entityType: String, entityId: String): Int

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLead(id: String)

    @Query("DELETE FROM leads WHERE serverId IS NULL OR lower(name) LIKE '%demo%' OR (name = 'Ramesh Patel' AND normalizedPhone = '+919876543210') OR (name = 'Anita Sharma' AND normalizedPhone = '+919812345678')")
    suspend fun deleteDemoLeads()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE status = 'OPEN'")
    fun observeOpenConflictCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_events WHERE status IN ('PENDING', 'FAILED')")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT status, entityType, COUNT(*) AS count FROM sync_events WHERE status != 'SYNCED' GROUP BY status, entityType ORDER BY status, entityType")
    fun observeSyncQueueBreakdown(): Flow<List<SyncQueueBucket>>

    @Query("SELECT * FROM follow_ups ORDER BY scheduledAt")
    fun observeAllFollowUps(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE status = 'PENDING' AND scheduledAt <= :until ORDER BY scheduledAt LIMIT 25")
    suspend fun dueFollowUps(until: Long): List<FollowUpEntity>

    @Query("UPDATE follow_ups SET status = 'COMPLETED', updatedAt = :at, version = version + 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun completeFollowUp(id: String, at: Long)

    @Query("UPDATE follow_ups SET scheduledAt = :scheduledAt, note = :note, status = 'PENDING', updatedAt = :at, version = version + 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun updateFollowUp(id: String, scheduledAt: Long, note: String?, at: Long)

    @Query("UPDATE follow_ups SET status = 'CANCELLED', updatedAt = :at, version = version + 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun cancelFollowUp(id: String, at: Long)
}
