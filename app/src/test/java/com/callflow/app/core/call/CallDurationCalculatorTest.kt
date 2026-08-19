package com.callflow.app.core.call

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CallDurationCalculatorTest {
    @Test fun derivesDurationsFromLifecycleTimestamps() {
        val start = Instant.parse("2026-08-19T06:00:00Z")
        val result = CallDurationCalculator.calculate(start, start.plusSeconds(10), start.plusSeconds(70))
        assertEquals(CallDurations(10, 60, 70), result)
    }
}
