package com.callflow.app.ui.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.CreateLeadResult
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.NewLead
import com.callflow.app.core.model.TimelineItem
import com.callflow.app.core.model.LeadCallStats
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.domain.usecase.PrioritizeCallingQueue
import com.callflow.app.data.remote.IntroductionStatusDto
import com.callflow.app.data.remote.defaultIntroductionStatuses
import com.callflow.app.data.remote.IntroductionInvitationDto
import com.callflow.app.data.remote.IntroductionConfirmationRequest
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.remote.EngagementConfigResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class LeadsUiState(
    val statusOptions: List<IntroductionStatusDto> = defaultIntroductionStatuses(),
    val invitations: List<IntroductionInvitationDto> = emptyList(),
    val selectedSession: String = "ALL",
    val selectedConfirmation: String = "ALL",
    val confirmedCount: Int = 0,
    val invitationError: String? = null,
    val query: String = "",
    val selectedFilter: String = "ALL",
    val leads: List<Lead> = emptyList(),
    val matchingLeadCount: Int = 0,
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val stageCounts: Map<String, Int> = emptyMap(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val dateMode: LeadDateMode = LeadDateMode.NONE,
    val selectedSource: String = "ALL",
    val selectedQuality: String = "ALL",
    val selectedCity: String = "ALL",
    val selectedScore: String = "ALL",
    val selectedCallability: String = "ALL",
    val selectedDuplicates: String = "ALL",
    val selectedContactStatus: String = "ALL",
    val selectedSort: String = "PRIORITY",
    val sources: List<String> = emptyList(),
    val qualities: List<String> = emptyList(),
    val cities: List<String> = emptyList(),
    val activeFilterCount: Int = 0,
    val contactStats: Map<String, LeadCallStats> = emptyMap(),
    val neverContacted: Int = 0,
    val overdue: Int = 0,
)

enum class LeadDateMode { NONE, TODAY, CUSTOM }

private data class LeadFilters(
    val session: String = "ALL",
    val confirmation: String = "ALL",
    val stage: String = "ALL",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val dateMode: LeadDateMode = LeadDateMode.NONE,
    val source: String = "ALL",
    val quality: String = "ALL",
    val city: String = "ALL",
    val score: String = "ALL",
    val callability: String = "ALL",
    val duplicates: String = "ALL",
    val contactStatus: String = "ALL",
    val sort: String = "PRIORITY",
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeadsViewModel @Inject constructor(repository: LeadRepository, prioritize: PrioritizeCallingQueue, private val api: CallFlowApi) : ViewModel() {
    private val statusOptions = MutableStateFlow(defaultIntroductionStatuses())
    private val invitations = MutableStateFlow<List<IntroductionInvitationDto>>(emptyList())
    private val invitationError = MutableStateFlow<String?>(null)
    private val invitationState = combine(invitations, invitationError, statusOptions) { rows, error, options -> Triple(rows, error, options) }
    init { refreshInvitations() }
    fun refreshInvitations() { viewModelScope.launch {
        runCatching { api.introductionInvitations() }.onSuccess { statusOptions.value = it.statusOptions; invitations.value = it.invitations; invitationError.value = null }
            .onFailure { invitationError.value = introductionSessionError(it) }
    } }
    fun setSession(value: String) { filters.value = filters.value.copy(session = value) }
    fun setConfirmation(value: String) { filters.value = filters.value.copy(confirmation = value) }
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(LeadFilters())
    val state: StateFlow<LeadsUiState> = combine(repository.observeAllAssignedLeads(), repository.observeCallStats(), query.debounce(180), filters, invitationState) { source, callStats, q, filter, invitationData ->
        val (invites, inviteError, options) = invitationData
        val prioritized = prioritize(source) + source.filter(Lead::doNotCall).sortedByDescending(Lead::updatedAt)
        val filtered = prioritized.asSequence()
            .filter { lead -> q.isBlank() || listOf(lead.name, lead.displayPhone, lead.company.orEmpty(), lead.city.orEmpty(), lead.campaignId.orEmpty(), lead.quality.orEmpty(), lead.score.toString()).any { it.contains(q, ignoreCase = true) } }
            .filter { lead ->
                val date = lead.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                (filter.startDate == null || !date.isBefore(filter.startDate)) && (filter.endDate == null || !date.isAfter(filter.endDate))
            }
            .filter { lead -> when (filter.stage) {
                "ALL" -> true
                "NEW" -> lead.isNew()
                "OLD" -> !lead.isNew()
                else -> lead.stageId.equals(filter.stage, ignoreCase = true)
            } }
            .filter { lead -> matchesIntroductionInvitation(lead.id, invites, filter.session, filter.confirmation) }
            .filter { filter.source == "ALL" || it.campaignId.equals(filter.source, true) }
            .filter { filter.quality == "ALL" || it.quality.equals(filter.quality, true) }
            .filter { filter.city == "ALL" || it.city.equals(filter.city, true) }
            .filter { it.matchesScore(filter.score) }
            .filter { filter.callability == "ALL" || filter.callability == "CALLABLE" && !it.doNotCall || filter.callability == "DNC" && it.doNotCall }
            .filter { filter.duplicates == "ALL" || filter.duplicates == "UNIQUE" && it.duplicateCount <= 1 || filter.duplicates == "DUPLICATE" && it.duplicateCount > 1 }
            .filter { lead -> lead.matchesContactStatus(filter.contactStatus, callStats[lead.id], java.time.Instant.now()) }
            .toMutableList()
        when (filter.sort) {
            "NEWEST" -> filtered.sortByDescending(Lead::updatedAt)
            "OLDEST" -> filtered.sortBy(Lead::updatedAt)
            "SCORE_HIGH" -> filtered.sortByDescending(Lead::score)
            "SCORE_LOW" -> filtered.sortBy(Lead::score)
            "NAME" -> filtered.sortBy { it.name.lowercase() }
            "LAST_CONTACT" -> filtered.sortByDescending { callStats[it.id]?.lastContactedAt ?: java.time.Instant.EPOCH }
        }
        val activeCount = listOf(filter.session, filter.confirmation, filter.stage, filter.source, filter.quality, filter.city, filter.score, filter.callability, filter.duplicates, filter.contactStatus).count { it != "ALL" } +
            (if (filter.dateMode != LeadDateMode.NONE) 1 else 0) + (if (filter.sort != "PRIORITY") 1 else 0)
        val todayStart = java.time.Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
        LeadsUiState(
            statusOptions = options, invitations = invites, selectedSession = filter.session, selectedConfirmation = filter.confirmation, invitationError = inviteError,
            confirmedCount = confirmedIntroductionLeadCount(filtered.map { it.id }, invites, options, filter.session),
            query = q, selectedFilter = filter.stage, leads = filtered.take(100), matchingLeadCount = filtered.size, totalLeads = source.size, newLeads = source.count(Lead::isNew), stageCounts = source.groupingBy { it.stageId }.eachCount(),
            startDate = filter.startDate, endDate = filter.endDate, dateMode = filter.dateMode, selectedSource = filter.source, selectedQuality = filter.quality, selectedCity = filter.city,
            selectedScore = filter.score, selectedCallability = filter.callability, selectedDuplicates = filter.duplicates, selectedContactStatus = filter.contactStatus, selectedSort = filter.sort,
            sources = source.mapNotNull(Lead::campaignId).filter(String::isNotBlank).distinct().sorted(), qualities = source.mapNotNull(Lead::quality).filter(String::isNotBlank).distinct().sorted(),
            cities = source.mapNotNull(Lead::city).filter(String::isNotBlank).distinct().sorted(), activeFilterCount = activeCount, contactStats = callStats,
            neverContacted = source.count { (callStats[it.id]?.attempts ?: 0) == 0 }, overdue = source.count { it.nextFollowUpAt?.isBefore(todayStart) == true },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadsUiState())
    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: String) { filters.value = filters.value.copy(stage = value) }
    fun setSource(value: String) { filters.value = filters.value.copy(source = value) }
    fun setQuality(value: String) { filters.value = filters.value.copy(quality = value) }
    fun setCity(value: String) { filters.value = filters.value.copy(city = value) }
    fun setScore(value: String) { filters.value = filters.value.copy(score = value) }
    fun setCallability(value: String) { filters.value = filters.value.copy(callability = value) }
    fun setDuplicates(value: String) { filters.value = filters.value.copy(duplicates = value) }
    fun setContactStatus(value: String) { filters.value = filters.value.copy(contactStatus = value) }
    fun setSort(value: String) { filters.value = filters.value.copy(sort = value) }
    fun setTodayDateFilter(today: LocalDate = LocalDate.now()) {
        filters.value = filters.value.copy(startDate = today, endDate = today, dateMode = LeadDateMode.TODAY)
    }
    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        val (from, to) = normalizedDateRange(startDate, endDate)
        filters.value = filters.value.copy(startDate = from, endDate = to, dateMode = LeadDateMode.CUSTOM)
    }
    fun clearDateFilter() { filters.value = filters.value.copy(startDate = null, endDate = null, dateMode = LeadDateMode.NONE) }
    fun clearAllFilters() { filters.value = LeadFilters() }
}

private fun Lead.isNew() = stageId.contains("new", ignoreCase = true)
private fun Lead.matchesScore(filter: String) = when (filter) {
    "0-25" -> score in 0..25
    "26-50" -> score in 26..50
    "51-75" -> score in 51..75
    "76-100" -> score >= 76
    else -> true
}

private fun Lead.matchesContactStatus(filter: String, stats: LeadCallStats?, now: java.time.Instant): Boolean = when (filter) {
    "NEVER_CONTACTED" -> (stats?.attempts ?: 0) == 0
    "CONTACTED" -> (stats?.attempts ?: 0) > 0
    "CONNECTED" -> (stats?.connected ?: 0) > 0
    "NEVER_CONNECTED" -> (stats?.attempts ?: 0) > 0 && (stats?.connected ?: 0) == 0
    "DUE" -> nextFollowUpAt?.let { !it.isAfter(now) } == true
    "OVERDUE" -> nextFollowUpAt?.isBefore(now.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()) == true
    else -> true
}

internal fun filterLeadsByDate(leads: List<Lead>, startDate: LocalDate?, endDate: LocalDate?, zoneId: ZoneId = ZoneId.systemDefault()): List<Lead> {
    if (startDate == null && endDate == null) return leads
    return leads.filter { lead ->
        val date = lead.updatedAt.atZone(zoneId).toLocalDate()
        (startDate == null || !date.isBefore(startDate)) && (endDate == null || !date.isAfter(endDate))
    }
}

internal fun normalizedDateRange(startDate: LocalDate, endDate: LocalDate): Pair<LocalDate, LocalDate> =
    minOf(startDate, endDate) to maxOf(startDate, endDate)

data class LeadDetailUiState(
    val lead: Lead? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val stats: LeadCallStats = LeadCallStats(""),
    val statusOptions: List<IntroductionStatusDto> = defaultIntroductionStatuses(),
    val invitations: List<IntroductionInvitationDto> = emptyList(),
    val invitationError: String? = null,
    val confirmationSaving: Boolean = false,
    val engagement: EngagementConfigResponse? = null,
    val loading: Boolean = true,
    val engagementLoading: Boolean = true,
    val pendingHandover: Boolean = false,
    val handoverSaving: Boolean = false,
    val handoverError: String? = null,
)

@HiltViewModel
class LeadDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: LeadRepository, private val api: CallFlowApi) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["leadId"])
    private val engagement = MutableStateFlow<EngagementConfigResponse?>(null)
    private val engagementAttempted = MutableStateFlow(false)
    private val handoverSaving = MutableStateFlow(false)
    private val handoverError = MutableStateFlow<String?>(null)
    private val baseState = combine(repository.observeLead(id), repository.observeTimeline(id), repository.observeCallStats(id), engagement, engagementAttempted) { lead, timeline, stats, config, attempted ->
        val latestHandover = timeline.filter { it.title == "Lead handover" }.maxByOrNull(TimelineItem::occurredAt)
        val latestAck = timeline.filter { it.title == "Handover accepted" }.maxByOrNull(TimelineItem::occurredAt)
        LeadDetailUiState(lead = lead, timeline = timeline, stats = stats, engagement = config, loading = false, engagementLoading = !attempted, pendingHandover = latestHandover != null && (latestAck == null || latestAck.occurredAt.isBefore(latestHandover.occurredAt)))
    }
    private val detailState = combine(baseState, handoverSaving, handoverError) { base, saving, error ->
        base.copy(handoverSaving = saving, handoverError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())
    private val statusOptions = MutableStateFlow(defaultIntroductionStatuses())
    private val invitations = MutableStateFlow<List<IntroductionInvitationDto>>(emptyList())
    private val invitationError = MutableStateFlow<String?>(null)
    private val confirmationSaving = MutableStateFlow(false)
    val state = combine(detailState, invitations, invitationError, confirmationSaving, statusOptions) { base, rows, error, saving, options ->
        base.copy(statusOptions = options, invitations = rows, invitationError = error, confirmationSaving = saving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())
    init { refreshEngagement(); refreshInvitations() }
    fun refreshInvitations() { viewModelScope.launch {
        runCatching { api.introductionInvitations() }.onSuccess { statusOptions.value = it.statusOptions; invitations.value = it.invitations.filter { row -> row.leadId == id }; invitationError.value = null }
            .onFailure { invitationError.value = introductionSessionError(it) }
    } }
    fun confirmIntroduction(registrationId: String, status: String) {
        if (confirmationSaving.value) return
        confirmationSaving.value = true
        viewModelScope.launch {
            runCatching { api.confirmIntroduction(IntroductionConfirmationRequest(id, registrationId, status)) }
                .onSuccess { statusOptions.value = it.statusOptions; invitations.value = it.invitations.filter { row -> row.leadId == id }; invitationError.value = null }
                .onFailure { invitationError.value = "Confirmation was not saved. Connect and retry." }
            confirmationSaving.value = false
        }
    }
    fun refreshEngagement() { engagementAttempted.value = false; viewModelScope.launch { runCatching { api.engagementConfig() }.onSuccess { engagement.value = it }; engagementAttempted.value = true } }
    fun acknowledgeHandover() {
        if (handoverSaving.value) return
        handoverSaving.value = true
        handoverError.value = null
        viewModelScope.launch { repository.acknowledgeHandover(id).fold(onSuccess = { handoverSaving.value = false }, onFailure = { handoverSaving.value = false; handoverError.value = it.message ?: "Could not confirm handover" }) }
    }
}

internal fun matchesIntroductionInvitation(leadId: String, invitations: List<IntroductionInvitationDto>, session: String, status: String): Boolean =
    if (session == "ALL" && status == "ALL") true else invitations.any {
        it.leadId == leadId && (session == "ALL" || it.sessionId == session) && (status == "ALL" || it.status == status)
    }

private fun introductionSessionError(error: Throwable): String =
    if (error is retrofit2.HttpException && error.code() == 409) "Introduction sessions are not connected for this company workspace."
    else "Could not load session confirmations. Connect and retry."

internal fun confirmedIntroductionLeadCount(leadIds: List<String>, invitations: List<IntroductionInvitationDto>, options: List<IntroductionStatusDto>, session: String): Int {
    val confirmedStatuses = options.filter { it.isConfirmed }.map { it.id }.toSet()
    val confirmedLeads = invitations.filter { (session == "ALL" || it.sessionId == session) && it.status in confirmedStatuses }.map { it.leadId }.toSet()
    return leadIds.distinct().count { it in confirmedLeads }
}
