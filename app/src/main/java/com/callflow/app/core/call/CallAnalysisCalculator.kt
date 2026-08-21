package com.callflow.app.core.call

import com.callflow.app.core.model.CallRecord
import java.time.Duration

data class CallAnalysis(
    val totalCalls: Int = 0,
    val connectedCalls: Int = 0,
    val connectionRatePercent: Int = 0,
    val totalTalkTimeSeconds: Long = 0,
    val averageTalkTimeSeconds: Long = 0,
)

object CallAnalysisCalculator {
    fun calculate(calls: List<CallRecord>): CallAnalysis {
        val connected = calls.filter { it.answeredAt != null }
        val durations = connected.mapNotNull { call ->
            val answeredAt = call.answeredAt ?: return@mapNotNull null
            val endedAt = call.endedAt ?: return@mapNotNull null
            Duration.between(answeredAt, endedAt).seconds.coerceAtLeast(0)
        }
        val totalTalkTime = durations.sum()
        return CallAnalysis(
            totalCalls = calls.size,
            connectedCalls = connected.size,
            connectionRatePercent = if (calls.isEmpty()) 0 else (connected.size * 100f / calls.size).toInt(),
            totalTalkTimeSeconds = totalTalkTime,
            averageTalkTimeSeconds = if (durations.isEmpty()) 0 else totalTalkTime / durations.size,
        )
    }
}
