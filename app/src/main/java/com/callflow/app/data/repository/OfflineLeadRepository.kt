package com.callflow.app.data.repository

import com.callflow.app.BuildConfig
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.NewLead
import com.callflow.app.core.model.CreateLeadResult
import com.callflow.app.core.model.TimelineItem
import com.callflow.app.core.model.LeadCallStats
import com.callflow.app.data.local.LeadCallSummary
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.LeadEntity
import com.callflow.app.data.local.NoteEntity
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.domain.repository.LeadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class OfflineLeadRepository @Inject constructor(
    private val dao: CallFlowDao,
    private val clock: DateTimeProvider,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
) : LeadRepository {
    override fun observeCallingQueue(): Flow<List<Lead>> = dao.observeCallingQueue().map { rows -> rows.assignedOnly().map(LeadEntity::toDomain) }
    override fun observeAllAssignedLeads(): Flow<List<Lead>> = dao.observeAllLeads().map { rows -> rows.assignedOnly().map(LeadEntity::toDomain) }
    override fun search(query: String): Flow<List<Lead>> = dao.searchLeads(query.trim(), query.filter(Char::isDigit)).map { rows -> rows.assignedOnly().map(LeadEntity::toDomain) }
    override fun observeLead(id: String): Flow<Lead?> = dao.observeLead(id).map { it?.takeIf { row -> BuildConfig.USE_FAKE_BACKEND || row.serverId != null }?.toDomain() }
    override suspend fun findByPhone(phone: String): Lead? {
        val normalized = phoneNumberNormalizer.normalize(phone) ?: return null
        return dao.findByPhone(normalized).assignedOnly().firstOrNull()?.toDomain()
    }

    override fun observeTimeline(leadId: String): Flow<List<TimelineItem>> = combine(
        dao.observeCalls(leadId), dao.observeNotes(leadId), dao.observeFollowUps(leadId),
    ) { calls, notes, followUps ->
        buildList {
            calls.forEach { call ->
                val connected = call.answeredAt != null
                val status = if (connected) "Connected" else if (call.direction == "INCOMING") "Missed" else "Not connected"
                val seconds = if (connected && call.endedAt != null) ((call.endedAt - checkNotNull(call.answeredAt)) / 1000).coerceAtLeast(0) else 0
                val callDetail = buildList {
                    if (connected) add(durationLabel(seconds)) else call.failureReason?.takeIf(String::isNotBlank)?.let(::add)
                    call.employeeId.takeIf(String::isNotBlank)?.let { add("Salesperson: $it") }
                }.joinToString(" · ").ifBlank { null }
                add(TimelineItem(call.id, "CALL", Instant.ofEpochMilli(call.startedAt), "${call.direction.lowercase().replaceFirstChar(Char::uppercase)} call · $status", callDetail, call.employeeId.takeIf(String::isNotBlank)))
            }
            notes.forEach {
                val title = when { it.body.startsWith("HANDOVER_ACK:") -> "Handover accepted"; it.body.startsWith("HANDOVER:") -> "Lead handover"; it.body.startsWith("HISTORY:") -> "Previous activity"; else -> "Note added" }
                val detail = if (title == "Note added") it.body else it.body.substringAfter(':').trim()
                add(TimelineItem(it.id, "NOTE", Instant.ofEpochMilli(it.createdAt), title, detail, it.createdBy.takeIf(String::isNotBlank)))
            }
            followUps.forEach { add(TimelineItem(it.id, "FOLLOW_UP", Instant.ofEpochMilli(it.createdAt), "Follow-up ${it.status.lowercase()}", Instant.ofEpochMilli(it.scheduledAt).toString(), it.assignedTo.takeIf(String::isNotBlank))) }
        }.sortedByDescending(TimelineItem::occurredAt)
    }

    override fun observeCallStats(): Flow<Map<String, LeadCallStats>> = dao.observeLeadCallSummaries().map { rows -> rows.associate { it.leadId to it.toDomain() } }
    override fun observeCallStats(leadId: String): Flow<LeadCallStats> = observeCallStats().map { it[leadId] ?: LeadCallStats(leadId) }

    override suspend fun createLead(value: NewLead): CreateLeadResult {
        if (!BuildConfig.USE_FAKE_BACKEND) return CreateLeadResult.Invalid("Leads must be assigned from the dashboard")
        if (value.name.isBlank()) return CreateLeadResult.Invalid("Name is required")
        val normalized = phoneNumberNormalizer.normalize(value.phone) ?: return CreateLeadResult.Invalid("Enter a valid phone number")
        dao.findByPhone(normalized).firstOrNull()?.let { return CreateLeadResult.Duplicate(it.toDomain()) }
        val now = clock.now().toEpochMilli()
        val id = UUID.randomUUID().toString()
        dao.insertLead(LeadEntity(id, null, value.name.trim(), value.company?.trim()?.ifBlank { null }, value.city?.trim()?.ifBlank { null }, normalized, value.phone.trim(), "new", "local-user", null, null, now, "local-user", 1))
        return CreateLeadResult.Created(id)
    }

    override suspend fun seedIfEmpty() {
        if (!BuildConfig.USE_FAKE_BACKEND) return
        if (dao.observeCallingQueue(1).first().isNotEmpty()) return
        // Development-only seed through the fake repository boundary; production builds replace this binding.
        val now = clock.now()
        dao.upsertLeads(listOf(
            LeadEntity(UUID.randomUUID().toString(), null, "Ramesh Patel", "ABC Textile", "Surat", "+919876543210", "+91 98765 43210", "interested", "local-user", null, now.plusSeconds(3600).toEpochMilli(), now.toEpochMilli(), "local-user", 1),
            LeadEntity(UUID.randomUUID().toString(), null, "Anita Sharma", "Northstar Foods", "Pune", "+919812345678", "+91 98123 45678", "hot", "local-user", null, now.minusSeconds(1800).toEpochMilli(), now.toEpochMilli(), "local-user", 1),
        ))
    }

    override suspend fun acknowledgeHandover(leadId: String): Result<Unit> = runCatching {
        require(dao.getLead(leadId) != null) { "Lead is no longer assigned" }
        val now = clock.now().toEpochMilli()
        val noteId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val body = "HANDOVER_ACK: Handover received and pending follow-ups reviewed"
        dao.insertNoteWithOutbox(
            NoteEntity(noteId, leadId, null, body, now, "local-user", "local-install", SyncStatus.PENDING.name),
            SyncEventEntity(eventId, eventId, "NOTE", noteId, "CREATE", "{\"leadId\":\"$leadId\",\"body\":\"$body\",\"createdAt\":$now}", now, 0, null, SyncStatus.PENDING.name, null),
        )
    }
}

private fun LeadCallSummary.toDomain() = LeadCallStats(leadId, attempts, connected, missed, notConnected, talkTimeSeconds, firstContactedAt?.let(Instant::ofEpochMilli), lastContactedAt?.let(Instant::ofEpochMilli))
private fun durationLabel(seconds: Long): String { val minutes = seconds / 60; val remainder = seconds % 60; return if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s" }

private fun LeadEntity.toDomain() = Lead(
    id, serverId, name, company, city, normalizedPhone, displayPhone, stageId, assignedUserId,
    campaignId, nextFollowUpAt?.let(Instant::ofEpochMilli), Instant.ofEpochMilli(updatedAt), version,
    doNotCall, duplicateCount, score, quality, email, interest, state, country, assignedTo, bestTime,
    revenuePotential, createdAt?.let(Instant::ofEpochMilli), tags.unpack(), sourceDetails.unpack(), workshopsAttended.unpack(),
)
private fun String?.unpack(): List<String> = this?.split('\u001F')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
private fun List<LeadEntity>.assignedOnly() = if (BuildConfig.USE_FAKE_BACKEND) this else filter { it.serverId != null && !it.isKnownDemoLead() }
private fun LeadEntity.isKnownDemoLead() =
    name.contains("demo", ignoreCase = true) ||
    (name == "Ramesh Patel" && normalizedPhone == "+919876543210") ||
        (name == "Anita Sharma" && normalizedPhone == "+919812345678")
