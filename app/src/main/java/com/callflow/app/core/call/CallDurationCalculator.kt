package com.callflow.app.core.call

import java.time.Duration
import java.time.Instant

data class CallDurations(val ringSeconds: Long, val connectedSeconds: Long, val totalSeconds: Long)

object CallDurationCalculator {
    fun calculate(startedAt: Instant, answeredAt: Instant?, endedAt: Instant): CallDurations {
        require(!endedAt.isBefore(startedAt))
        require(answeredAt == null || (!answeredAt.isBefore(startedAt) && !answeredAt.isAfter(endedAt)))
        return CallDurations(
            ringSeconds = Duration.between(startedAt, answeredAt ?: endedAt).seconds,
            connectedSeconds = answeredAt?.let { Duration.between(it, endedAt).seconds } ?: 0,
            totalSeconds = Duration.between(startedAt, endedAt).seconds,
        )
    }
}
