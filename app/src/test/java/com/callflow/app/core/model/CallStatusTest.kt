package com.callflow.app.core.model

import java.time.Instant
import org.junit.Test
import org.junit.Assert.assertEquals

class CallStatusTest {
    private fun call(direction: CallDirection, answered: Boolean) = CallRecord(
        id = "call", leadId = null, phone = "9999999999", direction = direction,
        startedAt = Instant.EPOCH, answeredAt = if (answered) Instant.EPOCH.plusSeconds(1) else null,
        endedAt = Instant.EPOCH.plusSeconds(2), failureReason = null, syncStatus = SyncStatus.PENDING,
    )

    @Test fun connected_when_answered() = assertEquals(CallStatus.CONNECTED, call(CallDirection.OUTGOING, true).status)
    @Test fun missed_only_for_unanswered_incoming() = assertEquals(CallStatus.MISSED, call(CallDirection.INCOMING, false).status)
    @Test fun not_connected_for_unanswered_outgoing() = assertEquals(CallStatus.NOT_CONNECTED, call(CallDirection.OUTGOING, false).status)
}
