package com.callflow.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore("sync_metadata")

@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun current(): String? = context.syncDataStore.data.first()[CURSOR]
    suspend fun update(value: String) { context.syncDataStore.edit { it[CURSOR] = value } }
    companion object { private val CURSOR = stringPreferencesKey("delta_cursor") }
}
