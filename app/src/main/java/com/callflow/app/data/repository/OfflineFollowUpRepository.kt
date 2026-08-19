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

class OfflineFollowUpRepository @Inject constructor(private val database: CallFlowDatabase, private val dao: CallFlowDao, private val clock: DateTimeProvider) : FollowUpRepository {
    override fun observeAll(): Flow<List<FollowUpRecord>> = dao.observeAllFollowUps().map { rows -> rows.map(FollowUpEntity::toDomain) }
    override suspend fun complete(id: String): Result<Unit> = runCatching {
        val now = clock.now().toEpochMilli(); val eventId = UUID.randomUUID().toString()
        database.withTransaction {
            dao.completeFollowUp(id, now)
            dao.insertSyncEvent(SyncEventEntity(eventId, eventId, "FOLLOW_UP", id, "COMPLETE", "{\"id\":\"$id\"}", now, 0, null, SyncStatus.PENDING.name, null))
        }
    }
}
private fun FollowUpEntity.toDomain() = FollowUpRecord(id, leadId, Instant.ofEpochMilli(scheduledAt), note, FollowUpStatus.valueOf(status), priority)
