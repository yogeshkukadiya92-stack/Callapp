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

    @Query("SELECT * FROM calls WHERE leadId = :leadId ORDER BY startedAt DESC")
    fun observeCalls(leadId: String): Flow<List<CallEntity>>

    @Query("SELECT * FROM follow_ups WHERE leadId = :leadId ORDER BY scheduledAt DESC")
    fun observeFollowUps(leadId: String): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeads(leads: List<LeadEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLead(lead: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCall(call: CallEntity)

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

    @Query("SELECT * FROM dispositions WHERE active = 1 ORDER BY sortOrder")
    fun observeDispositions(): Flow<List<DispositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDispositions(values: List<DispositionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLeadStages(values: List<LeadStageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppConfiguration(values: List<AppConfigurationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncEvent(event: SyncEventEntity)

    @Transaction
    suspend fun insertCallAttemptWithOutbox(call: CallEntity, lifecycle: CallEventEntity, event: SyncEventEntity) {
        insertCall(call)
        insertCallEvent(lifecycle)
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

    @Query("UPDATE sync_events SET status = 'FAILED', lastError = :error WHERE eventUuid IN (:eventUuids)")
    suspend fun markSyncFailed(eventUuids: List<String>, error: String)

    @Query("SELECT COUNT(*) FROM sync_events WHERE entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'SYNCING', 'FAILED')")
    suspend fun pendingMutationCount(entityType: String, entityId: String): Int

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLead(id: String)

    @Query("DELETE FROM leads WHERE serverId IS NULL AND updatedBy = 'local-user'")
    suspend fun deleteDemoLeads()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: SyncConflictEntity)

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE status = 'OPEN'")
    fun observeOpenConflictCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_events WHERE status IN ('PENDING', 'FAILED')")
    fun observePendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM follow_ups ORDER BY scheduledAt")
    fun observeAllFollowUps(): Flow<List<FollowUpEntity>>

    @Query("UPDATE follow_ups SET status = 'COMPLETED', updatedAt = :at, version = version + 1, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun completeFollowUp(id: String, at: Long)
}
