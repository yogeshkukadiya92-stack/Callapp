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
    val query: String = "",
    val selectedFilter: String = "ALL",
    val leads: List<Lead> = emptyList(),
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val stageCounts: Map<String, Int> = emptyMap(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
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

private data class LeadFilters(
    val stage: String = "ALL",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
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
class LeadsViewModel @Inject constructor(repository: LeadRepository, prioritize: PrioritizeCallingQueue) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(LeadFilters())
    val state: StateFlow<LeadsUiState> = combine(repository.observeAllAssignedLeads(), repository.observeCallStats(), query.debounce(180), filters) { source, callStats, q, filter ->
        val prioritized = prioritize(source) + source.filter(Lead::doNotCall).sortedByDescending(Lead::updatedAt)
        val searched = if (q.isBlank()) prioritized else prioritized.filter { lead -> listOf(lead.name, lead.displayPhone, lead.company.orEmpty(), lead.city.orEmpty(), lead.campaignId.orEmpty(), lead.quality.orEmpty(), lead.score.toString()).any { it.contains(q, ignoreCase = true) } }
        val dated = filterLeadsByDate(searched, filter.startDate, filter.endDate)
        val staged = when (filter.stage) {
            "ALL" -> dated
            "NEW" -> dated.filter(Lead::isNew)
            "OLD" -> dated.filterNot(Lead::isNew)
            else -> dated.filter { it.stageId.equals(filter.stage, ignoreCase = true) }
        }
        val refined = staged
            .filter { filter.source == "ALL" || it.campaignId.equals(filter.source, true) }
            .filter { filter.quality == "ALL" || it.quality.equals(filter.quality, true) }
            .filter { filter.city == "ALL" || it.city.equals(filter.city, true) }
            .filter { it.matchesScore(filter.score) }
            .filter { filter.callability == "ALL" || filter.callability == "CALLABLE" && !it.doNotCall || filter.callability == "DNC" && it.doNotCall }
            .filter { filter.duplicates == "ALL" || filter.duplicates == "UNIQUE" && it.duplicateCount <= 1 || filter.duplicates == "DUPLICATE" && it.duplicateCount > 1 }
            .filter { lead -> lead.matchesContactStatus(filter.contactStatus, callStats[lead.id], java.time.Instant.now()) }
        val visible = when (filter.sort) {
            "NEWEST" -> refined.sortedByDescending(Lead::updatedAt)
            "OLDEST" -> refined.sortedBy(Lead::updatedAt)
            "SCORE_HIGH" -> refined.sortedByDescending(Lead::score)
            "SCORE_LOW" -> refined.sortedBy(Lead::score)
            "NAME" -> refined.sortedBy { it.name.lowercase() }
            "LAST_CONTACT" -> refined.sortedByDescending { callStats[it.id]?.lastContactedAt ?: java.time.Instant.EPOCH }
            else -> refined
        }
        val activeCount = listOf(filter.stage, filter.source, filter.quality, filter.city, filter.score, filter.callability, filter.duplicates, filter.contactStatus).count { it != "ALL" } +
            listOf(filter.startDate, filter.endDate).count { it != null } + if (filter.sort != "PRIORITY") 1 else 0
        val todayStart = java.time.Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
        LeadsUiState(
            query = q, selectedFilter = filter.stage, leads = visible, totalLeads = source.size, newLeads = source.count(Lead::isNew), stageCounts = source.groupingBy { it.stageId }.eachCount(),
            startDate = filter.startDate, endDate = filter.endDate, selectedSource = filter.source, selectedQuality = filter.quality, selectedCity = filter.city,
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
    fun setStartDate(value: LocalDate) {
        filters.value = filters.value.copy(startDate = value, endDate = filters.value.endDate?.takeUnless { it.isBefore(value) } ?: value)
    }
    fun setEndDate(value: LocalDate) {
        filters.value = filters.value.copy(endDate = value, startDate = filters.value.startDate?.takeUnless { it.isAfter(value) } ?: value)
    }
    fun clearDateFilter() { filters.value = filters.value.copy(startDate = null, endDate = null) }
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

data class LeadDetailUiState(val lead: Lead? = null, val timeline: List<TimelineItem> = emptyList(), val stats: LeadCallStats = LeadCallStats(""), val engagement: EngagementConfigResponse? = null, val loading: Boolean = true, val engagementLoading: Boolean = true)

@HiltViewModel
class LeadDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: LeadRepository, private val api: CallFlowApi) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["leadId"])
    private val engagement = MutableStateFlow<EngagementConfigResponse?>(null)
    private val engagementAttempted = MutableStateFlow(false)
    val state = combine(repository.observeLead(id), repository.observeTimeline(id), repository.observeCallStats(id), engagement, engagementAttempted) { lead, timeline, stats, config, attempted -> LeadDetailUiState(lead, timeline, stats, config, loading = false, engagementLoading = !attempted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())
    init { refreshEngagement() }
    fun refreshEngagement() { engagementAttempted.value = false; viewModelScope.launch { runCatching { api.engagementConfig() }.onSuccess { engagement.value = it }; engagementAttempted.value = true } }
}
