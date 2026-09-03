package com.callflow.app.ui.home

import com.callflow.app.core.model.DailyMetrics
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.PriorityLead
import com.callflow.app.core.model.QueuePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DailyGoalPlanTest {
    @Test fun dashboardConfigurationControlsFallbackTargetsAndRemainingWork() {
        val plan = dailyGoalPlan(
            metrics = DailyMetrics(calls = 20, connected = 8, talkTimeSeconds = 1_800),
            performance = null,
            queue = emptyList(),
            configured = mapOf("sales_daily_call_target" to "40", "sales_daily_connected_target" to "16", "sales_daily_talk_minutes" to "60"),
        )

        assertEquals(20, plan.callsRemaining)
        assertEquals(8, plan.connectedRemaining)
        assertEquals(1_800L, plan.talkTimeRemainingSeconds)
        assertEquals(50, plan.overallProgressPercent)
    }

    @Test fun overdueFollowUpAlwaysBecomesTheNextBestAction() {
        val lead = Lead("lead-1", null, "Asha", null, null, "+911", "+911", "new", "sales", null, Instant.now(), Instant.now(), 1)
        val plan = dailyGoalPlan(DailyMetrics(), null, listOf(PriorityLead(lead, QueuePriority.OVERDUE)), emptyMap())

        assertTrue(plan.nextAction.contains("overdue follow-up"))
    }

    @Test fun completedTargetsNeverShowNegativeRemainingValues() {
        val plan = dailyGoalPlan(DailyMetrics(calls = 100, connected = 50, talkTimeSeconds = 10_000), null, emptyList(), emptyMap())
        assertEquals(0, plan.callsRemaining)
        assertEquals(0, plan.connectedRemaining)
        assertEquals(0L, plan.talkTimeRemainingSeconds)
        assertEquals(100, plan.overallProgressPercent)
    }
}
