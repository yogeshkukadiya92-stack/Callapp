package com.callflow.app.data.repository

import androidx.room.withTransaction
import com.callflow.app.core.model.FollowUpRecord
import com.callflow.app.core.model.FollowUpStatus
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.FollowUpEntity
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.domain.repository.FollowUpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.callflow.app.notifications.FollowUpReminderScheduler
import org.json.JSONObject

class OfflineFollowUpRepository @Inject constructor(private val database: CallFlowDatabase, private val dao: CallFlowDao, private val clock: DateTimeProvider, private val notifications: FollowUpReminderScheduler = FollowUpReminderScheduler.NoOp) : FollowUpRepository {
    override fun observeAll(): Flow<List<FollowUpRecord>> = dao.observeAllFollowUps().map { rows -> rows.map(FollowUpEntity::toDomain) }
    override suspend fun complete(id: String): Result<Unit> = runCatching {
        val now = clock.now().toEpochMilli(); val eventId = UUID.randomUUID().toString()
        val followUp = requireNotNull(dao.getFollowUp(id)) { "Follow-up not found" }
        database.withTransaction {
            dao.completeFollowUp(id, now)
            dao.insertSyncEvent(SyncEventEntity(eventId, eventId, "FOLLOW_UP", id, "COMPLETE", "{\"id\":\"$id\",\"leadId\":\"${followUp.leadId}\",\"completedAt\":$now}", now, 0, null, SyncStatus.PENDING.name, null))
        }
        notifications.cancel(id)
    }
    override suspend fun update(id: String, scheduledAt: Instant, note: String?): Result<Unit> = runCatching {
        require(scheduledAt.isAfter(clock.now())) { "Follow-up time must be in the future" }
        val now = clock.now().toEpochMilli(); val eventId = UUID.randomUUID().toString()
        val followUp = requireNotNull(dao.getFollowUp(id)) { "Follow-up not found" }
        val cleanNote = note?.trim()?.ifBlank { null }
        val payload = JSONObject().put("id", id).put("leadId", followUp.leadId).put("scheduledAt", scheduledAt.toEpochMilli()).put("note", cleanNote ?: JSONObject.NULL).put("updatedAt", now).toString()
        database.withTransaction {
            dao.updateFollowUp(id, scheduledAt.toEpochMilli(), cleanNote, now)
            dao.insertSyncEvent(SyncEventEntity(eventId, eventId, "FOLLOW_UP", id, "UPDATE", payload, now, 0, null, SyncStatus.PENDING.name, null))
        }
        notifications.schedule(id, scheduledAt)
    }
    override suspend fun cancel(id: String): Result<Unit> = runCatching {
        val now = clock.now().toEpochMilli(); val eventId = UUID.randomUUID().toString()
        val followUp = requireNotNull(dao.getFollowUp(id)) { "Follow-up not found" }
        val payload = JSONObject().put("id", id).put("leadId", followUp.leadId).put("cancelledAt", now).toString()
        database.withTransaction {
            dao.cancelFollowUp(id, now)
            dao.insertSyncEvent(SyncEventEntity(eventId, eventId, "FOLLOW_UP", id, "CANCEL", payload, now, 0, null, SyncStatus.PENDING.name, null))
        }
        notifications.cancel(id)
    }
}
private fun FollowUpEntity.toDomain() = FollowUpRecord(id, leadId, Instant.ofEpochMilli(scheduledAt), note, FollowUpStatus.valueOf(status), priority, type)
