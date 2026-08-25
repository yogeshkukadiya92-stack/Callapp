package com.callflow.app.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header

@JsonClass(generateAdapter = true)
data class LoginRequest(val identity: String, val password: String? = null, val otp: String? = null)
@JsonClass(generateAdapter = true)
data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresAt: String, val employeeName: String? = null, val mobile: String? = null)
@JsonClass(generateAdapter = true)
data class AssignmentAvailabilityRequest(val acceptingLeads: Boolean, val latitude: Double? = null, val longitude: Double? = null, val accuracyMeters: Float? = null, val capturedAt: String? = null)
@JsonClass(generateAdapter = true)
data class AssignmentAvailabilityResponse(val acceptingLeads: Boolean, val changedAt: String? = null)
@JsonClass(generateAdapter = true)
data class TodayPerformanceResponse(val date: String, val callTarget: Int, val connectedTarget: Int, val calls: Int, val connected: Int, val connectionRate: Int, val talkTimeSeconds: Long, val conversions: Int, val followUpsDue: Int, val callTargetPercent: Int, val connectedTargetPercent: Int, val leaderboardRank: Int = 0, val leaderboardSize: Int = 0)
@JsonClass(generateAdapter = true)
data class ShiftDayResponse(val date: String, val shiftStartedAt: String? = null, val shiftEndedAt: String? = null, val activeSeconds: Long = 0, val calls: Int = 0, val connected: Int = 0, val firstCallAt: String? = null, val lastCallAt: String? = null, val callsPerActiveHour: Double = 0.0)
@JsonClass(generateAdapter = true)
data class ShiftSummaryResponse(val today: ShiftDayResponse, val last7Days: List<ShiftDayResponse> = emptyList(), val totalActiveSeconds: Long = 0, val totalCalls: Int = 0)
@JsonClass(generateAdapter = true)
data class EngagementConfigResponse(val whatsappTemplate: String, val salespersonName: String)
@JsonClass(generateAdapter = true)
data class LocationCheckInRequest(val followUpId: String, val leadId: String, val latitude: Double, val longitude: Double, val accuracyMeters: Float, val capturedAt: String)
@JsonClass(generateAdapter = true)
data class LocationCheckInResponse(val ok: Boolean, val checkedInAt: String)
@JsonClass(generateAdapter = true)
data class SyncEventDto(val eventUuid: String, val entityType: String, val entityId: String, val operation: String, val payload: Map<String, Any?>)
@JsonClass(generateAdapter = true)
data class BatchSyncRequest(val deviceId: String, val lastSyncCursor: String?, val events: List<SyncEventDto>)
@JsonClass(generateAdapter = true)
data class BatchSyncResponse(val acceptedEventIds: List<String>, val failedEventIds: List<String>, val nextSyncCursor: String?, val serverTimestamp: String)
@JsonClass(generateAdapter = true)
data class DeviceRegistrationRequest(val installId: String, val deviceName: String, val manufacturer: String, val model: String, val androidVersion: String, val appVersion: String)
@JsonClass(generateAdapter = true)
data class DeviceRegistrationResponse(val deviceId: String, val status: String)
@JsonClass(generateAdapter = true)
data class LeadDeltaDto(
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
    val nextFollowUpAt: Long?,
    val updatedAt: Long,
    val updatedBy: String,
    val version: Long,
    val doNotCall: Boolean = false,
    val duplicateCount: Int = 1,
)
@JsonClass(generateAdapter = true)
data class CallDeltaDto(val id: String, val serverId: String?, val leadId: String?, val employeeId: String, val campaignId: String?, val normalizedPhone: String, val direction: String, val startedAt: Long, val answeredAt: Long?, val endedAt: Long?, val failureReason: String?)
@JsonClass(generateAdapter = true)
data class CallEventDeltaDto(val id: String, val callId: String, val type: String, val occurredAt: Long)
@JsonClass(generateAdapter = true)
data class NoteDeltaDto(val id: String, val leadId: String, val callId: String?, val body: String, val createdAt: Long, val createdBy: String, val deviceId: String)
@JsonClass(generateAdapter = true)
data class FollowUpDeltaDto(val id: String, val leadId: String, val scheduledAt: Long, val note: String?, val priority: Int, val assignedTo: String, val type: String, val status: String, val createdAt: Long, val updatedAt: Long, val version: Long)
@JsonClass(generateAdapter = true)
data class LeadStageDeltaDto(val id: String, val code: String, val name: String, val sortOrder: Int, val active: Boolean)
@JsonClass(generateAdapter = true)
data class DispositionDeltaDto(val id: String, val code: String, val name: String, val icon: String?, val sortOrder: Int, val active: Boolean, val requiresNote: Boolean, val requiresFollowUp: Boolean, val targetStageId: String?)
@JsonClass(generateAdapter = true)
data class AppConfigurationDeltaDto(val key: String, val value: String, val updatedAt: Long)
@JsonClass(generateAdapter = true)
data class DashboardConnectorStatusDto(
    val connectorId: String,
    val dashboardName: String,
    val status: String,
    val syncDirection: String,
    val lastSuccessfulSyncAt: String?,
    val capabilities: List<String> = emptyList(),
)
@JsonClass(generateAdapter = true)
data class DeltaSyncResponse(
    val leads: List<LeadDeltaDto> = emptyList(),
    val calls: List<CallDeltaDto> = emptyList(),
    val callEvents: List<CallEventDeltaDto> = emptyList(),
    val notes: List<NoteDeltaDto> = emptyList(),
    val followUps: List<FollowUpDeltaDto> = emptyList(),
    val leadStages: List<LeadStageDeltaDto> = emptyList(),
    val dispositions: List<DispositionDeltaDto> = emptyList(),
    val appConfiguration: List<AppConfigurationDeltaDto> = emptyList(),
    val deletedLeadIds: List<String> = emptyList(),
    val deletedFollowUpIds: List<String> = emptyList(),
    val nextCursor: String,
    val serverTimestamp: String,
)

interface CallFlowApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): TokenResponse
    @POST("auth/refresh") suspend fun refresh(@Body refreshToken: Map<String, String>): TokenResponse
    @POST("devices/register") suspend fun registerDevice(@Header("Authorization") authorization: String, @Body request: DeviceRegistrationRequest): DeviceRegistrationResponse
    @GET("crm/status") suspend fun connectorStatus(): DashboardConnectorStatusDto
    @GET("availability") suspend fun assignmentAvailability(): AssignmentAvailabilityResponse
    @POST("availability") suspend fun updateAssignmentAvailability(@Body request: AssignmentAvailabilityRequest): AssignmentAvailabilityResponse
    @GET("performance/today") suspend fun todayPerformance(): TodayPerformanceResponse
    @GET("shifts/summary") suspend fun shiftSummary(): ShiftSummaryResponse
    @GET("engagement/config") suspend fun engagementConfig(): EngagementConfigResponse
    @POST("location/check-in") suspend fun locationCheckIn(@Body request: LocationCheckInRequest): LocationCheckInResponse
    @POST("sync/batch") suspend fun batchSync(@Body request: BatchSyncRequest): BatchSyncResponse
    @GET("sync/changes") suspend fun changes(@Query("cursor") cursor: String?): DeltaSyncResponse
}

interface RefreshTokenApi {
    @POST("auth/refresh") suspend fun refresh(@Body refreshToken: Map<String, String>): TokenResponse
}
