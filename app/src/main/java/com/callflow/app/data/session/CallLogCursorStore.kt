package com.callflow.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.callLogImportDataStore by preferencesDataStore("call_log_import")

@Singleton
class CallLogCursorStore @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun lastImportedAt(): Long = context.callLogImportDataStore.data.first()[LAST_IMPORTED_AT] ?: 0L
    suspend fun updateLastImportedAt(value: Long) { context.callLogImportDataStore.edit { it[LAST_IMPORTED_AT] = value } }

    companion object {
        private val LAST_IMPORTED_AT = longPreferencesKey("last_imported_at")
    }
}
