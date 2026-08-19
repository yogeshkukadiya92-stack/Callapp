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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LeadsUiState(val query: String = "", val leads: List<Lead> = emptyList())

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeadsViewModel @Inject constructor(repository: LeadRepository, prioritize: PrioritizeCallingQueue) : ViewModel() {
    private val query = MutableStateFlow("")
    val state: StateFlow<LeadsUiState> = query.debounce(180).flatMapLatest { q ->
        val source = if (q.isBlank()) repository.observeCallingQueue() else repository.search(q)
        source.combine(query) { leads, current -> LeadsUiState(current, if (q.isBlank()) prioritize(leads) else leads) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadsUiState())
    fun setQuery(value: String) { query.value = value }
}

data class LeadDetailUiState(val lead: Lead? = null, val timeline: List<TimelineItem> = emptyList())

@HiltViewModel
class LeadDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: LeadRepository) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["leadId"])
    val state = combine(repository.observeLead(id), repository.observeTimeline(id), ::LeadDetailUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeadDetailUiState())
}
