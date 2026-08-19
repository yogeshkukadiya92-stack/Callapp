package com.callflow.app.data.repository

import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.domain.repository.MetricsRepository
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.FollowUpRepository
import com.callflow.app.core.time.DateTimeProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalMetricsRepository @Inject constructor(
    private val calls: CallRepository,
    private val followUps: FollowUpRepository,
    private val clock: DateTimeProvider,
) : MetricsRepository {
    override fun observeToday(): Flow<DailyMetrics> = combine(calls.observeRecentCalls(), followUps.observeAll()) { callRows, followUpRows ->
        val now = clock.now(); val start = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val todayCalls = callRows.filter { !it.startedAt.isBefore(start) }
        val connected = todayCalls.filter { it.answeredAt != null }
        DailyMetrics(calls = todayCalls.size, connected = connected.size, meaningful = connected.count { it.endedAt != null && java.time.Duration.between(it.answeredAt, it.endedAt).seconds >= 180 }, talkTimeSeconds = connected.sumOf { if (it.endedAt != null) java.time.Duration.between(it.answeredAt, it.endedAt).seconds else 0 }, followUpsDue = followUpRows.count { it.status == com.callflow.app.core.model.FollowUpStatus.PENDING && !it.scheduledAt.isAfter(now) }, conversions = 0)
    }
}
