package com.callflow.app.data.repository

import androidx.room.withTransaction
import com.callflow.app.BuildConfig
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.remote.BatchSyncRequest
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.remote.SyncEventDto
import com.callflow.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.callflow.app.data.session.SyncCursorStore
import com.callflow.app.sync.DeltaSyncApplier
import com.callflow.app.data.session.SyncHealthStore
import com.callflow.app.core.model.SyncHealth
import com.callflow.app.data.session.SessionTokenStore

class OutboxSyncRepository @Inject constructor(
    private val database: CallFlowDatabase,
    private val dao: CallFlowDao,
    private val api: CallFlowApi,
    private val clock: DateTimeProvider,
    private val cursors: SyncCursorStore,
    private val deltaApplier: DeltaSyncApplier,
    private val healthStore: SyncHealthStore,
    private val sessions: SessionTokenStore,
) : SyncRepository {
    override fun observePendingCount(): Flow<Int> = dao.observePendingSyncCount()
    override fun observeConflictCount(): Flow<Int> = dao.observeOpenConflictCount()
    override fun observeHealth(): Flow<SyncHealth> = healthStore.health

    override suspend fun syncPending(): Result<Unit> = runCatching {
        val attemptAt = clock.now().toEpochMilli()
        healthStore.attempted(attemptAt)
        val events = dao.pendingSyncEvents(100)
        if (BuildConfig.USE_FAKE_BACKEND) {
            if (events.isNotEmpty()) dao.markSynced(events.map { it.eventUuid })
            healthStore.succeeded(clock.now().toEpochMilli())
            return@runCatching
        }
        try {
            val cursor = cursors.current()
            if (events.isNotEmpty()) {
                database.withTransaction { dao.markSyncing(events.map { it.id }, clock.now().toEpochMilli()) }
                val deviceId = sessions.current()?.deviceId ?: error("Registered device session is required for sync")
                val response = api.batchSync(BatchSyncRequest(deviceId, cursor, events.map { SyncEventDto(it.eventUuid, it.entityType, it.entityId, it.operation, mapOf("raw" to it.payload)) }))
                if (response.acceptedEventIds.isNotEmpty()) dao.markSynced(response.acceptedEventIds)
                if (response.failedEventIds.isNotEmpty()) dao.markSyncFailed(response.failedEventIds, "Server rejected this event")
            }
            deltaApplier.apply(api.changes(cursor))
            healthStore.succeeded(clock.now().toEpochMilli())
        } catch (error: Exception) {
            if (events.isNotEmpty()) dao.markSyncFailed(events.map { it.eventUuid }, error.message?.take(300) ?: "Sync failed")
            healthStore.failed(clock.now().toEpochMilli(), error.message ?: "Sync failed")
            throw error
        }
    }
}
