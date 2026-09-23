package com.callflow.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.simPreferenceDataStore by preferencesDataStore("sim_preferences")

data class SimSyncConfig(
    val selectedSlot: Int = 0, // 0 = All SIMs, 1 = SIM 1, 2 = SIM 2
    val subscriptionId: String? = null,
    val displayLabel: String? = null,
)

@Singleton
class SimPreferenceStore @Inject constructor(@ApplicationContext private val context: Context) {
    val selectedSimSlot: Flow<Int> = context.simPreferenceDataStore.data.map { it[SELECTED_SIM_SLOT] ?: 0 }
    val simSyncConfig: Flow<SimSyncConfig> = context.simPreferenceDataStore.data.map { prefs ->
        SimSyncConfig(
            selectedSlot = prefs[SELECTED_SIM_SLOT] ?: 0,
            subscriptionId = prefs[SELECTED_SUBSCRIPTION_ID],
            displayLabel = prefs[SELECTED_DISPLAY_LABEL],
        )
    }

    suspend fun getSelectedSimSlot(): Int = context.simPreferenceDataStore.data.first()[SELECTED_SIM_SLOT] ?: 0

    suspend fun setSimSyncSlot(slot: Int, subscriptionId: String? = null, displayLabel: String? = null) {
        context.simPreferenceDataStore.edit { prefs ->
            prefs[SELECTED_SIM_SLOT] = slot
            if (subscriptionId != null) prefs[SELECTED_SUBSCRIPTION_ID] = subscriptionId else prefs.remove(SELECTED_SUBSCRIPTION_ID)
            if (displayLabel != null) prefs[SELECTED_DISPLAY_LABEL] = displayLabel else prefs.remove(SELECTED_DISPLAY_LABEL)
        }
    }

    companion object {
        private val SELECTED_SIM_SLOT = intPreferencesKey("selected_sim_slot")
        private val SELECTED_SUBSCRIPTION_ID = stringPreferencesKey("selected_sim_subscription_id")
        private val SELECTED_DISPLAY_LABEL = stringPreferencesKey("selected_sim_display_label")
    }
}
