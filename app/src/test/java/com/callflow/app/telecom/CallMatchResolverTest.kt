package com.callflow.app.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallMatchResolverTest {
    @Test fun unique_lead_is_matched() {
        assertEquals("lead-1", CallMatchResolver.uniqueLeadId(listOf("lead-1")))
    }

    @Test fun duplicate_leads_are_left_unmatched() {
        assertNull(CallMatchResolver.uniqueLeadId(listOf("lead-1", "lead-2")))
    }

    @Test fun repeated_same_candidate_is_not_ambiguous() {
        assertEquals("lead-1", CallMatchResolver.uniqueLeadId(listOf("lead-1", "lead-1")))
    }

    @Test fun unique_recent_call_is_reused() {
        assertEquals("call-1", CallMatchResolver.uniqueOpenCallId(listOf("call-1")))
    }

    @Test fun multiple_recent_calls_are_never_guessed() {
        assertNull(CallMatchResolver.uniqueOpenCallId(listOf("call-newest", "call-older")))
    }

    @Test fun missing_candidates_remain_unmatched() {
        assertNull(CallMatchResolver.uniqueLeadId(emptyList()))
        assertNull(CallMatchResolver.uniqueOpenCallId(emptyList()))
    }
}
