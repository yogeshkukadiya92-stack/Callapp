package com.callflow.app.domain.repository

import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.NewLead
import com.callflow.app.core.model.CreateLeadResult
import com.callflow.app.core.model.TimelineItem
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.DispositionInput
import com.callflow.app.core.model.DispositionOption
import com.callflow.app.core.model.FollowUpRecord
import com.callflow.app.core.model.SessionState
import com.callflow.app.core.model.SyncHealth
import com.callflow.app.core.model.LeadCallStats
import kotlinx.coroutines.flow.Flow

interface LeadRepository {
    fun observeCallingQueue(): Flow<List<Lead>>
    fun observeAllAssignedLeads(): Flow<List<Lead>>
    fun search(query: String): Flow<List<Lead>>
    suspend fun seedIfEmpty()
    fun observeLead(id: String): Flow<Lead?>
    suspend fun findByPhone(phone: String): Lead?
    fun observeTimeline(leadId: String): Flow<List<TimelineItem>>
    fun observeCallStats(): Flow<Map<String, LeadCallStats>>
    fun observeCallStats(leadId: String): Flow<LeadCallStats>
    suspend fun createLead(value: NewLead): CreateLeadResult
}

interface MetricsRepository { fun observeToday(): Flow<DailyMetrics> }
interface SyncRepository {
    fun observePendingCount(): Flow<Int>
    fun observeConflictCount(): Flow<Int>
    fun observeHealth(): Flow<SyncHealth>
    suspend fun syncPending(): Result<Unit>
}

interface CallRepository {
    suspend fun startOutgoingCall(lead: Lead): Result<String>
    fun observeCall(callId: String): Flow<CallRecord?>
    fun observeRecentCalls(): Flow<List<CallRecord>>
    fun observeDispositions(): Flow<List<DispositionOption>>
    suspend fun saveDisposition(input: DispositionInput): Result<Unit>
    suspend fun addCallNote(callId: String, leadId: String, body: String): Result<Unit>
}

interface FollowUpRepository {
    fun observeAll(): Flow<List<FollowUpRecord>>
    suspend fun complete(id: String): Result<Unit>
    suspend fun update(id: String, scheduledAt: java.time.Instant, note: String?): Result<Unit>
    suspend fun cancel(id: String): Result<Unit>
}

interface AuthRepository {
    val session: Flow<SessionState>
    suspend fun login(identity: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun refreshDeviceStatus(): Result<Unit>
}
