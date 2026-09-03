package com.callflow.app.core.call

import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.CallStatus
import com.callflow.app.core.model.status
import java.time.Duration
import java.time.ZoneId

data class CallAnalysis(
    val totalCalls: Int = 0,
    val connectedCalls: Int = 0,
    val connectionRatePercent: Int = 0,
    val totalTalkTimeSeconds: Long = 0,
    val averageTalkTimeSeconds: Long = 0,
    val missedCalls: Int = 0,
    val notConnectedCalls: Int = 0,
    val incomingCalls: Int = 0,
    val outgoingCalls: Int = 0,
    val uniqueNumbers: Int = 0,
    val longestTalkTimeSeconds: Long = 0,
    val peakHour: Int? = null,
    val peakHourCalls: Int = 0,
)

object CallAnalysisCalculator {
    fun calculate(calls: List<CallRecord>, zoneId: ZoneId = ZoneId.systemDefault()): CallAnalysis {
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
            missedCalls = calls.count { it.status == CallStatus.MISSED },
            notConnectedCalls = calls.count { it.status == CallStatus.NOT_CONNECTED },
            incomingCalls = calls.count { it.direction == CallDirection.INCOMING },
            outgoingCalls = calls.count { it.direction == CallDirection.OUTGOING },
            uniqueNumbers = calls.map(CallRecord::phone).filter(String::isNotBlank).distinct().size,
            longestTalkTimeSeconds = durations.maxOrNull() ?: 0,
            peakHour = calls.groupingBy { it.startedAt.atZone(zoneId).hour }.eachCount().maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })?.key,
            peakHourCalls = calls.groupingBy { it.startedAt.atZone(zoneId).hour }.eachCount().maxOfOrNull { it.value } ?: 0,
        )
    }
}
