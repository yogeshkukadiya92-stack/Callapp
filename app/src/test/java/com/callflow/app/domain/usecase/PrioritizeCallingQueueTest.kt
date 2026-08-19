package com.callflow.app.domain.usecase

import com.callflow.app.core.model.Lead
import com.callflow.app.core.time.DateTimeProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PrioritizeCallingQueueTest {
    private val now = Instant.parse("2026-08-19T06:00:00Z")
    private val subject = PrioritizeCallingQueue(object : DateTimeProvider { override fun now() = this@PrioritizeCallingQueueTest.now })
    @Test fun prioritizesOverdueThenDueThenHotThenNew() {
        val values = listOf(lead("new", "new"), lead("hot", "hot"), lead("future", "contacted", now.plusSeconds(3600)), lead("overdue", "contacted", now.minusSeconds(1)))
        assertEquals(listOf("overdue", "future", "hot", "new"), subject(values).map(Lead::id))
    }
    private fun lead(id: String, stage: String, followUp: Instant? = null) = Lead(id, null, id, null, null, "+919999999999", "9999999999", stage, "user", null, followUp, now, 1)
}
