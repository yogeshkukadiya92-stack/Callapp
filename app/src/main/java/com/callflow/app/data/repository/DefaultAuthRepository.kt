package com.callflow.app.data.repository

import com.callflow.app.BuildConfig
import com.callflow.app.core.model.DeviceStatus
import com.callflow.app.core.model.SessionState
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.remote.LoginRequest
import com.callflow.app.data.session.EncryptedSessionStore
import com.callflow.app.data.session.StoredSession
import com.callflow.app.domain.repository.AuthRepository
import com.callflow.app.data.session.DeviceIdentityStore
import com.callflow.app.data.session.SyncCursorStore
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val api: CallFlowApi,
    private val store: EncryptedSessionStore,
    private val devices: DeviceIdentityStore,
    private val sync: SyncRepository,
    private val dao: CallFlowDao,
    private val cursors: SyncCursorStore,
) : AuthRepository {
    override val session: Flow<SessionState> = store.session.map { value -> value?.let { SessionState.SignedIn(it.employeeName, it.deviceStatus) } ?: SessionState.SignedOut }
    override suspend fun login(identity: String, password: String): Result<Unit> = runCatching {
        require(identity.isNotBlank()) { "Enter your mobile number or email" }
        require(password.length >= 4) { "Password must contain at least 4 characters" }
        val stored = if (BuildConfig.USE_FAKE_BACKEND) {
            val device = devices.registrationRequest()
            StoredSession("fake-${UUID.randomUUID()}", "fake-${UUID.randomUUID()}", identity.substringBefore('@').replaceFirstChar(Char::uppercase), DeviceStatus.ACTIVE, device.installId)
        } else {
            val token = api.login(LoginRequest(identity.trim(), password = password))
            val device = api.registerDevice("Bearer ${token.accessToken}", devices.registrationRequest())
            val status = parseDeviceStatus(device.status)
            StoredSession(token.accessToken, token.refreshToken, identity.substringBefore('@'), status, device.deviceId)
        }
        store.save(stored)
        if (!BuildConfig.USE_FAKE_BACKEND) {
            try {
                dao.deleteDemoLeads()
                cursors.clear()
                sync.syncPending().getOrThrow()
            } catch (error: Exception) {
                store.clear()
                throw error
            }
        }
    }
    override suspend fun logout() = store.clear()
    override suspend fun refreshDeviceStatus(): Result<Unit> = runCatching {
        val current = store.session.first() ?: error("Session expired")
        if (BuildConfig.USE_FAKE_BACKEND) {
            store.save(current.copy(deviceStatus = DeviceStatus.ACTIVE))
        } else {
            val response = api.registerDevice("Bearer ${current.accessToken}", devices.registrationRequest())
            val status = parseDeviceStatus(response.status)
            store.save(current.copy(deviceStatus = status, deviceId = response.deviceId))
        }
    }
}

internal fun parseDeviceStatus(value: String): DeviceStatus = runCatching { DeviceStatus.valueOf(value.uppercase()) }.getOrElse { DeviceStatus.PENDING_APPROVAL }
