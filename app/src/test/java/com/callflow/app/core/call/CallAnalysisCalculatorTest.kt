package com.callflow.app.core.call

import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CallAnalysisCalculatorTest {
    @Test fun `calculates connection rate and talk time`() {
        val start = Instant.parse("2026-08-21T10:00:00Z")
        val calls = listOf(
            call("1", start, start.plusSeconds(120)),
            call("2", start, start.plusSeconds(60)),
            call("3", null, null),
            call("4", null, null),
        )

        val result = CallAnalysisCalculator.calculate(calls)

        assertEquals(4, result.totalCalls)
        assertEquals(2, result.connectedCalls)
        assertEquals(50, result.connectionRatePercent)
        assertEquals(180L, result.totalTalkTimeSeconds)
        assertEquals(90L, result.averageTalkTimeSeconds)
    }

    @Test fun `returns zero values for empty history`() {
        assertEquals(CallAnalysis(), CallAnalysisCalculator.calculate(emptyList()))
    }

    @Test fun `calculates outcome direction unique number and peak hour metrics`() {
        val calls = listOf(
            call("1", Instant.parse("2026-08-21T09:00:05Z"), Instant.parse("2026-08-21T09:02:05Z"), CallDirection.OUTGOING, "+911", Instant.parse("2026-08-21T09:00:00Z")),
            call("2", null, Instant.parse("2026-08-21T09:20:10Z"), CallDirection.INCOMING, "+912", Instant.parse("2026-08-21T09:20:00Z")),
            call("3", null, Instant.parse("2026-08-21T11:00:10Z"), CallDirection.OUTGOING, "+911", Instant.parse("2026-08-21T11:00:00Z")),
        )

        val result = CallAnalysisCalculator.calculate(calls, ZoneOffset.UTC)
        assertEquals(1, result.connectedCalls)
        assertEquals(1, result.missedCalls)
        assertEquals(1, result.notConnectedCalls)
        assertEquals(1, result.incomingCalls)
        assertEquals(2, result.outgoingCalls)
        assertEquals(2, result.uniqueNumbers)
        assertEquals(120L, result.longestTalkTimeSeconds)
        assertEquals(9, result.peakHour)
        assertEquals(2, result.peakHourCalls)
    }

    private fun call(id: String, answeredAt: Instant?, endedAt: Instant?, direction: CallDirection = CallDirection.OUTGOING, phone: String = "+910000000000", startedAt: Instant = Instant.parse("2026-08-21T09:59:50Z")) = CallRecord(
        id = id,
        leadId = null,
        phone = phone,
        direction = direction,
        startedAt = startedAt,
        answeredAt = answeredAt,
        endedAt = endedAt,
        failureReason = null,
        syncStatus = SyncStatus.SYNCED,
    )
}
