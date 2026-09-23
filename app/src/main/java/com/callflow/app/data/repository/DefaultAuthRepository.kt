package com.callflow.app.data.repository

import android.content.Context
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
import com.callflow.app.sync.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.time.Instant
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val api: CallFlowApi,
    private val store: EncryptedSessionStore,
    private val devices: DeviceIdentityStore,
    private val dao: CallFlowDao,
    private val cursors: SyncCursorStore,
    @ApplicationContext private val context: Context,
) : AuthRepository {
    override val session: Flow<SessionState> = store.session.map { value -> value?.takeIf { offlineSessionValid(it.offlineValidUntilEpochMillis) }?.let { SessionState.SignedIn(it.employeeName, it.employeePhone, it.deviceStatus) } ?: SessionState.SignedOut }
    override suspend fun login(identity: String, password: String): Result<Unit> = runCatching {
        require(identity.isNotBlank()) { "Enter your mobile number or email" }
        require(password.length >= 4) { "Password must contain at least 4 characters" }
        val stored = if (BuildConfig.USE_FAKE_BACKEND) {
            val device = devices.registrationRequest()
            StoredSession("fake-${UUID.randomUUID()}", "fake-${UUID.randomUUID()}", identity.substringBefore('@').replaceFirstChar(Char::uppercase), DeviceStatus.ACTIVE, device.installId)
        } else {
            val registration = devices.registrationRequest()
            val token = api.login(LoginRequest(identity.trim(), password = password, installId = registration.installId, deviceName = registration.deviceName, manufacturer = registration.manufacturer, model = registration.model, androidVersion = registration.androidVersion, appVersion = registration.appVersion))
            val legacyDevice = if (token.deviceId.isNullOrBlank()) api.registerDevice("Bearer ${token.accessToken}", registration) else null
            val status = parseDeviceStatus(token.status ?: legacyDevice?.status ?: "ACTIVE")
            StoredSession(token.accessToken, token.refreshToken, token.employeeName?.ifBlank { null } ?: identity.substringBefore('@'), status, token.deviceId ?: legacyDevice?.deviceId, token.mobile?.ifBlank { null }, token.accountId, parseServerTime(token.offlineValidUntil) ?: Long.MAX_VALUE)
        }
        if (!BuildConfig.USE_FAKE_BACKEND) {
            dao.clearAccountData()
            cursors.clear()
        }
        store.save(stored)
        if (!BuildConfig.USE_FAKE_BACKEND) SyncWorker.syncAfterLogin(context)
    }
    override suspend fun logout() {
        if (!BuildConfig.USE_FAKE_BACKEND) runCatching { api.logout() }
        dao.clearAccountData()
        cursors.clear()
        store.clear()
    }
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
internal fun parseServerTime(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
internal fun offlineSessionValid(deadline: Long, now: Long = System.currentTimeMillis()) = deadline > now
