package com.callflow.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "leads", indices = [Index("serverId", unique = true), Index("normalizedPhone"), Index("stageId"), Index("assignedUserId"), Index("campaignId"), Index("nextFollowUpAt")])
data class LeadEntity(
    @PrimaryKey val id: String,
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
)

@Entity(tableName = "calls", indices = [Index("leadId"), Index("employeeId"), Index("startedAt"), Index("syncStatus")])
data class CallEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val leadId: String?,
    val employeeId: String,
    val campaignId: String?,
    val normalizedPhone: String,
    val direction: String,
    val startedAt: Long,
    val answeredAt: Long?,
    val endedAt: Long?,
    val failureReason: String?,
    val syncStatus: String,
)

@Entity(tableName = "call_events", indices = [Index("callId"), Index("occurredAt")])
data class CallEventEntity(@PrimaryKey val id: String, val callId: String, val type: String, val occurredAt: Long)

@Entity(tableName = "notes", indices = [Index("leadId"), Index("createdAt"), Index("syncStatus")])
data class NoteEntity(@PrimaryKey val id: String, val leadId: String, val callId: String?, val body: String, val createdAt: Long, val createdBy: String, val deviceId: String, val syncStatus: String)

@Entity(tableName = "follow_ups", indices = [Index("leadId"), Index("assignedTo"), Index("scheduledAt"), Index("status"), Index("syncStatus")])
data class FollowUpEntity(@PrimaryKey val id: String, val leadId: String, val scheduledAt: Long, val note: String?, val priority: Int, val assignedTo: String, val type: String, val status: String, val createdAt: Long, val updatedAt: Long, val version: Long, val syncStatus: String)

@Entity(tableName = "sync_events", indices = [Index("eventUuid", unique = true), Index("status"), Index("createdAt")])
data class SyncEventEntity(@PrimaryKey val id: String, val eventUuid: String, val entityType: String, val entityId: String, val operation: String, val payload: String, val createdAt: Long, val attemptCount: Int, val lastAttemptAt: Long?, val status: String, val lastError: String?)

@Entity(tableName = "app_configuration")
data class AppConfigurationEntity(@PrimaryKey val key: String, val value: String, val updatedAt: Long)

@Entity(tableName = "lead_stages", indices = [Index("sortOrder")])
data class LeadStageEntity(@PrimaryKey val id: String, val code: String, val name: String, val sortOrder: Int, val active: Boolean)

@Entity(tableName = "dispositions", indices = [Index("sortOrder")])
data class DispositionEntity(@PrimaryKey val id: String, val code: String, val name: String, val icon: String?, val sortOrder: Int, val active: Boolean, val requiresNote: Boolean, val requiresFollowUp: Boolean, val targetStageId: String?)

@Entity(tableName = "call_dispositions", indices = [Index("callId", unique = true), Index("leadId"), Index("createdAt"), Index("syncStatus")])
data class CallDispositionEntity(@PrimaryKey val id: String, val callId: String, val leadId: String, val dispositionId: String, val createdAt: Long, val createdBy: String, val syncStatus: String)

@Entity(tableName = "sync_conflicts", indices = [Index("entityType"), Index("entityId"), Index("status"), Index("createdAt")])
data class SyncConflictEntity(@PrimaryKey val id: String, val entityType: String, val entityId: String, val localVersion: Long, val serverVersion: Long, val localPayload: String, val serverPayload: String, val createdAt: Long, val status: String)
