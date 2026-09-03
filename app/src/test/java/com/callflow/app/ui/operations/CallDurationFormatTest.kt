package com.callflow.app.ui.operations

import com.callflow.app.core.call.CallAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test

class CallDurationFormatTest {
    @Test fun formatsExactMinutesAndSeconds() {
        assertEquals("0s", formatDuration(0))
        assertEquals("59s", formatDuration(59))
        assertEquals("1m 0s", formatDuration(60))
        assertEquals("2m 5s", formatDuration(125))
    }

    @Test fun formatsPeakHourForMorningNoonAndMidnight() {
        assertEquals("12:00 AM", hourLabel(0))
        assertEquals("9:00 AM", hourLabel(9))
        assertEquals("12:00 PM", hourLabel(12))
        assertEquals("7:00 PM", hourLabel(19))
    }

    @Test fun returnsActionableInsightForEmptyAndLowConnectionHistory() {
        assertEquals("Start calling to unlock performance insights.", callInsight(CallAnalysis()))
        assertEquals(
            "Low connection rate — retry unanswered leads during the peak hour.",
            callInsight(CallAnalysis(totalCalls = 10, connectedCalls = 2, connectionRatePercent = 20)),
        )
    }
}
