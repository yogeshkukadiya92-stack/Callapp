package com.callflow.app.core.model

import java.time.Instant

enum class CallDirection { INCOMING, OUTGOING }
enum class CallStatus { CONNECTED, MISSED, NOT_CONNECTED }
enum class CallEventType { INITIATED, RINGING, CONNECTED, ENDED, MISSED, REJECTED, FAILED }
enum class SyncStatus { PENDING, SYNCING, SYNCED, FAILED }
enum class FollowUpStatus { PENDING, COMPLETED, CANCELLED, RESCHEDULED, MISSED }

data class CallRecord(
    val id: String,
    val leadId: String?,
    val phone: String,
    val direction: CallDirection,
    val startedAt: Instant,
    val answeredAt: Instant?,
    val endedAt: Instant?,
    val failureReason: String?,
    val syncStatus: SyncStatus,
    val simSlot: Int? = null,
    val simLabel: String? = null,
    val phoneAccountId: String? = null,
)

val CallRecord.status: CallStatus
    get() = when {
        answeredAt != null -> CallStatus.CONNECTED
        direction == CallDirection.INCOMING -> CallStatus.MISSED
        else -> CallStatus.NOT_CONNECTED
    }

data class DispositionOption(
    val id: String,
    val code: String,
    val name: String,
    val requiresNote: Boolean,
    val requiresFollowUp: Boolean,
    val targetStageId: String?,
)

data class DispositionInput(
    val callId: String,
    val leadId: String,
    val disposition: DispositionOption,
    val note: String,
    val followUpAt: Instant?,
)

data class FollowUpRecord(
    val id: String,
    val leadId: String,
    val scheduledAt: Instant,
    val note: String?,
    val status: FollowUpStatus,
    val priority: Int,
    val type: String = "CALL",
)

data class Lead(
    val id: String,
    val serverId: String?,
    val name: String,
    val company: String?,
    val city: String?,
    val normalizedPhone: String,
    val displayPhone: String,
    val stageId: String,
    val assignedUserId: String,
    val campaignId: String?,
    val nextFollowUpAt: Instant?,
    val updatedAt: Instant,
    val version: Long,
    val doNotCall: Boolean = false,
    val duplicateCount: Int = 1,
    val score: Int = 0,
    val quality: String? = null,
)

enum class QueuePriority { OVERDUE, DUE_SOON, HOT, NEW, STANDARD }
data class PriorityLead(val lead: Lead, val priority: QueuePriority)

data class LeadCallStats(
    val leadId: String,
    val attempts: Int = 0,
    val connected: Int = 0,
    val missed: Int = 0,
    val notConnected: Int = 0,
    val talkTimeSeconds: Long = 0,
    val firstContactedAt: Instant? = null,
    val lastContactedAt: Instant? = null,
)

data class DailyMetrics(
    val calls: Int = 0,
    val connected: Int = 0,
    val meaningful: Int = 0,
    val talkTimeSeconds: Long = 0,
    val followUpsDue: Int = 0,
    val conversions: Int = 0,
)

data class SyncHealth(
    val lastAttemptAt: Instant? = null,
    val lastSuccessfulAt: Instant? = null,
    val lastError: String? = null,
)

data class TimelineItem(
    val id: String,
    val type: String,
    val occurredAt: Instant,
    val title: String,
    val detail: String?,
)

data class NewLead(
    val name: String,
    val phone: String,
    val company: String? = null,
    val city: String? = null,
)

sealed interface CreateLeadResult {
    data class Created(val leadId: String) : CreateLeadResult
    data class Duplicate(val existing: Lead) : CreateLeadResult
    data class Invalid(val reason: String) : CreateLeadResult
}

enum class DeviceStatus { ACTIVE, PENDING_APPROVAL, BLOCKED, REVOKED }
enum class PermissionState { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_REQUIRED, ROLE_MISSING }

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val employeeName: String, val employeePhone: String?, val deviceStatus: DeviceStatus) : SessionState
}

sealed interface AppError {
    data class Validation(val message: String) : AppError
    data object Authentication : AppError
    data object PermissionDenied : AppError
    data object NetworkUnavailable : AppError
    data object Timeout : AppError
    data class Conflict(val message: String) : AppError
    data class Server(val code: Int) : AppError
    data class Database(val cause: Throwable) : AppError
    data class Unknown(val cause: Throwable) : AppError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}
