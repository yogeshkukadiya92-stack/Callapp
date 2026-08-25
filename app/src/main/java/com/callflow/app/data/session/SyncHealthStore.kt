package com.callflow.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callflow.app.core.model.SyncHealth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncHealthDataStore by preferencesDataStore("sync_health")

@Singleton
class SyncHealthStore @Inject constructor(@ApplicationContext private val context: Context) {
    val health: Flow<SyncHealth> = context.syncHealthDataStore.data.map { values ->
        SyncHealth(
            lastAttemptAt = values[LAST_ATTEMPT]?.let(Instant::ofEpochMilli),
            lastSuccessfulAt = values[LAST_SUCCESS]?.let(Instant::ofEpochMilli),
            lastError = values[LAST_ERROR],
        )
    }

    suspend fun attempted(at: Long) = context.syncHealthDataStore.edit { it[LAST_ATTEMPT] = at }
    suspend fun succeeded(at: Long) = context.syncHealthDataStore.edit {
        it[LAST_SUCCESS] = at
        it.remove(LAST_ERROR)
    }
    suspend fun failed(at: Long, error: String) = context.syncHealthDataStore.edit {
        it[LAST_ATTEMPT] = at
        it[LAST_ERROR] = error.take(300)
    }

    private companion object {
        val LAST_ATTEMPT = longPreferencesKey("last_attempt_at")
        val LAST_SUCCESS = longPreferencesKey("last_success_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
