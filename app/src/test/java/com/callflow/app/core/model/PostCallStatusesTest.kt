package com.callflow.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PostCallStatusesTest {
    @Test fun requestedStatusesKeepTheirOrderAndDistinctStorageCodes() {
        assertEquals(listOf("Intro Attendees", "Warm", "Hot", "Meeting Generate", "Online", "Not Eligible", "Invite in intro", "Invite next intro", "Invite online intro", "Busy", "Out of network", "Wrong Number", "Not Connected", "Not Pickup Call", "Negative", "Call Back", "Online Meeting", "Not interested", "Other workshop", "UYP Registered"), postCallStatuses.map { it.name })
        assertEquals(20, postCallStatuses.map { it.id }.toSet().size)
        assertEquals(20, postCallStatuses.map { it.code }.toSet().size)
    }
}
