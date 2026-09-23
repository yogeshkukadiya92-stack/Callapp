package com.callflow.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3SessionTest {
    @Test fun offlineSessionExpiresAtServerDeadline() {
        assertTrue(offlineSessionValid(deadline = 2_000, now = 1_999))
        assertFalse(offlineSessionValid(deadline = 2_000, now = 2_000))
    }

    @Test fun serverTimeParsingIsStrictAndSafe() {
        assertTrue(parseServerTime("2026-09-06T10:00:00Z")!! > 0)
        assertNull(parseServerTime("not-a-date"))
        assertNull(parseServerTime(null))
    }
}
