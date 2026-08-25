package com.callflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.PriorityLead
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.domain.repository.MetricsRepository
import com.callflow.app.domain.usecase.PrioritizeCallingQueue
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.remote.TodayPerformanceResponse
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val metrics: DailyMetrics = DailyMetrics(), val queue: List<PriorityLead> = emptyList(), val performance: TodayPerformanceResponse? = null, val performanceLoading: Boolean = true)

@HiltViewModel
class HomeViewModel @Inject constructor(leads: LeadRepository, metrics: MetricsRepository, prioritize: PrioritizeCallingQueue, private val api: CallFlowApi) : ViewModel() {
    private val performance = MutableStateFlow<TodayPerformanceResponse?>(null)
    private val performanceAttempted = MutableStateFlow(false)
    val state: StateFlow<HomeUiState> = combine(metrics.observeToday(), leads.observeCallingQueue(), performance, performanceAttempted) { m, q, p, attempted ->
        HomeUiState(m, prioritize(q).map { PriorityLead(it, prioritize.priority(it)) }, p, !attempted)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    init { viewModelScope.launch { leads.seedIfEmpty() }; refreshPerformance() }
    fun refreshPerformance() { performanceAttempted.value = false; viewModelScope.launch { runCatching { api.todayPerformance() }.onSuccess { performance.value = it }; performanceAttempted.value = true } }
}
