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
import com.callflow.app.data.local.CallFlowDao
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyGoalPlan(
    val callTarget: Int,
    val connectedTarget: Int,
    val talkTimeTargetSeconds: Long,
    val callsRemaining: Int,
    val connectedRemaining: Int,
    val talkTimeRemainingSeconds: Long,
    val overallProgressPercent: Int,
    val nextAction: String,
)

internal fun dailyGoalPlan(metrics: DailyMetrics, performance: TodayPerformanceResponse?, queue: List<PriorityLead>, configured: Map<String, String>): DailyGoalPlan {
    val callTarget = (performance?.callTarget ?: configured["sales_daily_call_target"]?.toIntOrNull() ?: 50).coerceAtLeast(1)
    val connectedTarget = (performance?.connectedTarget ?: configured["sales_daily_connected_target"]?.toIntOrNull() ?: 20).coerceAtLeast(1)
    val talkTarget = ((configured["sales_daily_talk_minutes"]?.toLongOrNull() ?: 60L).coerceAtLeast(1) * 60)
    val calls = performance?.calls ?: metrics.calls
    val connected = performance?.connected ?: metrics.connected
    val talk = performance?.talkTimeSeconds ?: metrics.talkTimeSeconds
    val progress = listOf(calls * 100 / callTarget, connected * 100 / connectedTarget, (talk * 100 / talkTarget).toInt()).map { it.coerceIn(0, 100) }.average().toInt()
    val nextAction = when {
        queue.any { it.priority == com.callflow.app.core.model.QueuePriority.OVERDUE } -> "Call the overdue follow-up at the top of your queue."
        calls < callTarget -> "Make ${callTarget - calls} more calls to complete today’s call target."
        connected < connectedTarget -> "Retry never-connected leads to reach ${connectedTarget - connected} more conversations."
        talk < talkTarget -> "Focus on quality conversations for ${talkTarget - talk} more seconds."
        else -> "Today’s core activity targets are complete. Finish pending follow-ups."
    }
    return DailyGoalPlan(callTarget, connectedTarget, talkTarget, (callTarget - calls).coerceAtLeast(0), (connectedTarget - connected).coerceAtLeast(0), (talkTarget - talk).coerceAtLeast(0), progress, nextAction)
}

data class HomeUiState(val metrics: DailyMetrics = DailyMetrics(), val queue: List<PriorityLead> = emptyList(), val performance: TodayPerformanceResponse? = null, val performanceLoading: Boolean = true, val goalPlan: DailyGoalPlan = dailyGoalPlan(DailyMetrics(), null, emptyList(), emptyMap()))

@HiltViewModel
class HomeViewModel @Inject constructor(leads: LeadRepository, metrics: MetricsRepository, prioritize: PrioritizeCallingQueue, private val api: CallFlowApi, dao: CallFlowDao) : ViewModel() {
    private val performance = MutableStateFlow<TodayPerformanceResponse?>(null)
    private val performanceAttempted = MutableStateFlow(false)
    val state: StateFlow<HomeUiState> = combine(metrics.observeToday(), leads.observeCallingQueue(), performance, performanceAttempted, dao.observeAppConfiguration()) { m, q, p, attempted, configuration ->
        val queue = prioritize(q).map { PriorityLead(it, prioritize.priority(it)) }
        HomeUiState(m, queue, p, !attempted, dailyGoalPlan(m, p, queue, configuration.associate { it.key to it.value }))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
    init { viewModelScope.launch { leads.seedIfEmpty() }; refreshPerformance() }
    fun refreshPerformance() { performanceAttempted.value = false; viewModelScope.launch { runCatching { api.todayPerformance() }.onSuccess { performance.value = it }; performanceAttempted.value = true } }
}
