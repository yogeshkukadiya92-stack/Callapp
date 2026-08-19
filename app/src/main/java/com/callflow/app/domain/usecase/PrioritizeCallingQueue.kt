package com.callflow.app.domain.usecase

import com.callflow.app.core.model.Lead
import com.callflow.app.core.time.DateTimeProvider
import javax.inject.Inject

class PrioritizeCallingQueue @Inject constructor(private val clock: DateTimeProvider) {
    operator fun invoke(leads: List<Lead>): List<Lead> {
        val now = clock.now()
        return leads.sortedWith(compareBy<Lead> {
            when {
                it.nextFollowUpAt?.isBefore(now) == true -> 0
                it.nextFollowUpAt != null -> 1
                it.stageId.equals("hot", true) -> 2
                it.stageId.equals("new", true) -> 3
                else -> 4
            }
        }.thenBy { it.nextFollowUpAt }.thenByDescending { it.updatedAt })
    }
}
