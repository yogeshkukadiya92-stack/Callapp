package com.callflow.app.ui.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.CreateLeadResult
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.NewLead
import com.callflow.app.core.model.TimelineItem
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
import javax.inject.Inject

data class LeadsUiState(
    val query: String = "",
    val selectedFilter: String = "ALL",
    val leads: List<Lead> = emptyList(),
    val totalLeads: Int = 0,
    val newLeads: Int = 0,
    val stageCounts: Map<String, Int> = emptyMap(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeadsViewModel @Inject constructor(repository: LeadRepository, prioritize: PrioritizeCallingQueue) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow("ALL")
    val state: StateFlow<LeadsUiState> = combine(repository.observeCallingQueue(), query.debounce(180), selectedFilter) { source, q, filter ->
        val prioritized = prioritize(source)
        val searched = if (q.isBlank()) prioritized else prioritized.filter { lead -> listOf(lead.name, lead.displayPhone, lead.company.orEmpty(), lead.city.orEmpty()).any { it.contains(q, ignoreCase = true) } }
        val visible = when (filter) {
            "ALL" -> searched
            "NEW" -> searched.filter(Lead::isNew)
            "OLD" -> searched.filterNot(Lead::isNew)
            else -> searched.filter { it.stageId.equals(filter, ignoreCase = true) }
        }
        LeadsUiState(q, filter, visible, source.size, source.count(Lead::isNew), source.groupingBy { it.stageId }.eachCount())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadsUiState())
    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: String) { selectedFilter.value = value }
}

private fun Lead.isNew() = stageId.contains("new", ignoreCase = true)

data class LeadDetailUiState(val lead: Lead? = null, val timeline: List<TimelineItem> = emptyList(), val engagement: EngagementConfigResponse? = null, val loading: Boolean = true, val engagementLoading: Boolean = true)

@HiltViewModel
class LeadDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: LeadRepository, private val api: CallFlowApi) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["leadId"])
    private val engagement = MutableStateFlow<EngagementConfigResponse?>(null)
    private val engagementAttempted = MutableStateFlow(false)
    val state = combine(repository.observeLead(id), repository.observeTimeline(id), engagement, engagementAttempted) { lead, timeline, config, attempted -> LeadDetailUiState(lead, timeline, config, loading = false, engagementLoading = !attempted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())
    init { refreshEngagement() }
    fun refreshEngagement() { engagementAttempted.value = false; viewModelScope.launch { runCatching { api.engagementConfig() }.onSuccess { engagement.value = it }; engagementAttempted.value = true } }
}
