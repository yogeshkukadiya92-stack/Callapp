package com.callflow.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import com.callflow.app.core.model.DeviceStatus

private val Context.sessionDataStore by preferencesDataStore("secure_session")

data class StoredSession(val accessToken: String, val refreshToken: String, val employeeName: String, val deviceStatus: DeviceStatus = DeviceStatus.ACTIVE, val deviceId: String? = null)

interface SessionTokenStore {
    suspend fun save(value: StoredSession)
    suspend fun clear()
    suspend fun current(): StoredSession?
}

@Singleton
class EncryptedSessionStore @Inject constructor(@ApplicationContext private val context: Context) : SessionTokenStore {
    @Volatile private var cached: StoredSession? = null
    @Volatile private var cacheLoaded = false
    val session: Flow<StoredSession?> = context.sessionDataStore.data.map { preferences ->
        preferences[SESSION]?.let { runCatching { decode(decrypt(it)) }.getOrNull() }
    }

    override suspend fun save(value: StoredSession) { context.sessionDataStore.edit { it[SESSION] = encrypt(encode(value)) }; cached = value; cacheLoaded = true }
    override suspend fun clear() { context.sessionDataStore.edit { it.remove(SESSION) }; cached = null; cacheLoaded = true }
    override suspend fun current(): StoredSession? {
        if (cacheLoaded) return cached
        return session.first().also { cached = it; cacheLoaded = true }
    }

    private fun encode(value: StoredSession) = listOf(value.accessToken, value.refreshToken, value.employeeName, value.deviceStatus.name, value.deviceId.orEmpty()).joinToString("\u001F") { Base64.getEncoder().encodeToString(it.toByteArray()) }
    private fun decode(value: String): StoredSession {
        val fields = value.split("\u001F").map { String(Base64.getDecoder().decode(it)) }
        require(fields.size == 3 || fields.size == 5)
        return StoredSession(fields[0], fields[1], fields[2], fields.getOrNull(3)?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() } ?: DeviceStatus.ACTIVE, fields.getOrNull(4)?.ifBlank { null })
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(cipher.iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))
    }
    private fun decrypt(value: String): String {
        val pieces = value.split('.', limit = 2); require(pieces.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(pieces[0])))
        return String(cipher.doFinal(Base64.getDecoder().decode(pieces[1])))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        private val SESSION = stringPreferencesKey("encrypted_tokens")
        private const val ALIAS = "callflow_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
