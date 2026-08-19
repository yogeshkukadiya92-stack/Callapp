package com.callflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.core.model.Lead
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.domain.repository.MetricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(val metrics: DailyMetrics = DailyMetrics(), val queue: List<Lead> = emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(leads: LeadRepository, metrics: MetricsRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(metrics.observeToday(), leads.observeCallingQueue()) { m, q -> HomeUiState(m, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    init { viewModelScope.launch { leads.seedIfEmpty() } }
}
