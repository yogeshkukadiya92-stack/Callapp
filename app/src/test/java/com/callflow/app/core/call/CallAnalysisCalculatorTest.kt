package com.callflow.app.core.call

import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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

    private fun call(id: String, answeredAt: Instant?, endedAt: Instant?) = CallRecord(
        id = id,
        leadId = null,
        phone = "+910000000000",
        direction = CallDirection.OUTGOING,
        startedAt = Instant.parse("2026-08-21T09:59:50Z"),
        answeredAt = answeredAt,
        endedAt = endedAt,
        failureReason = null,
        syncStatus = SyncStatus.SYNCED,
    )
}
