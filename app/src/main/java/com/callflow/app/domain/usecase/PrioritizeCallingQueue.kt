package com.callflow.app.domain.usecase

import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.QueuePriority
import com.callflow.app.core.time.DateTimeProvider
import javax.inject.Inject

class PrioritizeCallingQueue @Inject constructor(private val clock: DateTimeProvider) {
    operator fun invoke(leads: List<Lead>): List<Lead> {
        val now = clock.now()
        return leads.filterNot(Lead::doNotCall).sortedWith(compareBy<Lead> {
            priority(it).ordinal
        }.thenBy { it.nextFollowUpAt }.thenByDescending { it.updatedAt })
    }

    fun priority(lead: Lead): QueuePriority {
        val now = clock.now()
        return when {
            lead.nextFollowUpAt?.isBefore(now) == true -> QueuePriority.OVERDUE
            lead.nextFollowUpAt != null && lead.nextFollowUpAt.isBefore(now.plusSeconds(24 * 60 * 60)) -> QueuePriority.DUE_SOON
            lead.stageId.equals("hot", true) -> QueuePriority.HOT
            lead.stageId.equals("new", true) || lead.stageId.equals("new leads", true) -> QueuePriority.NEW
            else -> QueuePriority.STANDARD
        }
    }
}
