package com.callflow.app.ui.leads

import com.callflow.app.core.model.Lead
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LeadCardDescriptionTest {
    @Test fun descriptionIncludesAvailableBusinessContext() {
        val lead = lead(company = "Northstar Foods", city = "Surat")
        val description = lead.shortDescription()
        assertTrue(description.contains("Northstar Foods"))
        assertTrue(description.contains("Surat"))
        assertTrue(description.contains("Updated"))
    }

    @Test fun descriptionHasUsefulFallback() {
        assertTrue(lead(company = null, city = null).shortDescription().startsWith("Assigned sales lead"))
    }

    private fun lead(company: String?, city: String?) = Lead("lead", "server", "Customer", company, city, "+911234567890", "+91 12345 67890", "new", "sales", null, null, Instant.parse("2026-08-29T08:00:00Z"), 1)
}
