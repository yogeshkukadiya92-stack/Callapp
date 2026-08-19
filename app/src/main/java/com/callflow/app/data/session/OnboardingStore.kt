package com.callflow.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore("onboarding")

@Singleton
class OnboardingStore @Inject constructor(@ApplicationContext private val context: Context) {
    val completed: Flow<Boolean> = context.onboardingDataStore.data.map { it[COMPLETED] ?: false }
    suspend fun complete() { context.onboardingDataStore.edit { it[COMPLETED] = true } }
    companion object { private val COMPLETED = booleanPreferencesKey("first_run_complete") }
}
