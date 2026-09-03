package com.callflow.app.ui.leads

import com.callflow.app.core.model.Lead
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LeadDateFilterTest {
    @Test fun dateRangeIsInclusive() {
        val leads = listOf(lead("before", "2026-08-09T23:59:59Z"), lead("start", "2026-08-10T00:00:00Z"), lead("end", "2026-08-20T23:59:59Z"), lead("after", "2026-08-21T00:00:00Z"))

        val result = filterLeadsByDate(leads, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-20"), ZoneOffset.UTC)

        assertEquals(listOf("start", "end"), result.map(Lead::id))
    }

    @Test fun singleDateReturnsOnlyThatDay() {
        val date = LocalDate.parse("2026-08-20")
        val leads = listOf(lead("match", "2026-08-20T12:30:00Z"), lead("other", "2026-08-19T12:30:00Z"))

        assertEquals(listOf("match"), filterLeadsByDate(leads, date, date, ZoneOffset.UTC).map(Lead::id))
    }

    private fun lead(id: String, updatedAt: String) = Lead(id, id, id, null, null, "+911234567890", "+91 12345 67890", "new", "sales", null, null, Instant.parse(updatedAt), 1)
}
