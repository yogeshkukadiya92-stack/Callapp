package com.callflow.app.ui.leads

import com.callflow.app.data.remote.IntroductionInvitationDto
import com.callflow.app.data.remote.IntroductionStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroductionInvitationFilterTest {
    private val invitations = listOf(
        IntroductionInvitationDto("registration-1", "lead-1", "session-1", "Intro", "2026-10-10", "Workshop", "pending"),
        IntroductionInvitationDto("registration-2", "lead-1", "session-2", "Intro", "2026-10-11", "Workshop", "confirmed"),
    )
    @Test fun confirmationMustBelongToSelectedSession() {
        assertFalse(matchesIntroductionInvitation("lead-1", invitations, "session-1", "confirmed"))
        assertTrue(matchesIntroductionInvitation("lead-1", invitations, "session-2", "confirmed"))
    }
    @Test fun allSessionsCanFindConfirmedLead() {
        assertTrue(matchesIntroductionInvitation("lead-1", invitations, "ALL", "confirmed"))
        assertFalse(matchesIntroductionInvitation("lead-2", invitations, "ALL", "confirmed"))
    }
    @Test fun unfilteredListIncludesUninvitedLeads() {
        assertTrue(matchesIntroductionInvitation("lead-2", invitations, "ALL", "ALL"))
        assertFalse(matchesIntroductionInvitation("lead-2", invitations, "session-1", "ALL"))
    }
    @Test fun customConfirmedStatusesCountUniqueLeadsInSelectedSession() {
        val rows = invitations + listOf(
            IntroductionInvitationDto("registration-3", "lead-1", "session-1", "Intro", "", "Workshop", "custom_ready"),
            IntroductionInvitationDto("registration-4", "lead-2", "session-2", "Intro", "", "Workshop", "custom_ready"),
        )
        val options = listOf(IntroductionStatusDto("custom_ready", "આવવાના છે", true, false))
        assertEquals(1, confirmedIntroductionLeadCount(listOf("lead-1", "lead-1", "lead-2"), rows, options, "session-1"))
        assertEquals(2, confirmedIntroductionLeadCount(listOf("lead-1", "lead-2"), rows, options, "ALL"))
        assertTrue(matchesIntroductionInvitation("lead-1", rows, "session-1", "custom_ready"))
    }
}
