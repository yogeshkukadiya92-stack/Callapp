package com.callflow.app.data.repository

import com.callflow.app.BuildConfig
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.NewLead
import com.callflow.app.core.model.CreateLeadResult
import com.callflow.app.core.model.TimelineItem
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.LeadEntity
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
    override fun observeCallingQueue(): Flow<List<Lead>> = dao.observeCallingQueue().map { rows -> rows.map(LeadEntity::toDomain) }
    override fun search(query: String): Flow<List<Lead>> = dao.searchLeads(query.trim(), query.filter(Char::isDigit)).map { rows -> rows.map(LeadEntity::toDomain) }
    override fun observeLead(id: String): Flow<Lead?> = dao.observeLead(id).map { it?.toDomain() }

    override fun observeTimeline(leadId: String): Flow<List<TimelineItem>> = combine(
        dao.observeCalls(leadId), dao.observeNotes(leadId), dao.observeFollowUps(leadId),
    ) { calls, notes, followUps ->
        buildList {
            calls.forEach { add(TimelineItem(it.id, "CALL", Instant.ofEpochMilli(it.startedAt), "${it.direction.lowercase().replaceFirstChar(Char::uppercase)} call", it.endedAt?.let { end -> "${(end - it.startedAt) / 1000}s" })) }
            notes.forEach { add(TimelineItem(it.id, "NOTE", Instant.ofEpochMilli(it.createdAt), "Note added", it.body)) }
            followUps.forEach { add(TimelineItem(it.id, "FOLLOW_UP", Instant.ofEpochMilli(it.createdAt), "Follow-up ${it.status.lowercase()}", Instant.ofEpochMilli(it.scheduledAt).toString())) }
        }.sortedByDescending(TimelineItem::occurredAt)
    }

    override suspend fun createLead(value: NewLead): CreateLeadResult {
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
}

private fun LeadEntity.toDomain() = Lead(id, serverId, name, company, city, normalizedPhone, displayPhone, stageId, assignedUserId, campaignId, nextFollowUpAt?.let(Instant::ofEpochMilli), Instant.ofEpochMilli(updatedAt), version)
