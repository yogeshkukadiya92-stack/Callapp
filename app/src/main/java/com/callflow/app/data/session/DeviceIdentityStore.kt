package com.callflow.app.data.session

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callflow.app.BuildConfig
import com.callflow.app.data.remote.DeviceRegistrationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceDataStore by preferencesDataStore("device_identity")

@Singleton
class DeviceIdentityStore @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun registrationRequest(): DeviceRegistrationRequest {
        val preferences = context.deviceDataStore.data.first()
        val installId = preferences[INSTALL_ID] ?: UUID.randomUUID().toString().also { generated -> context.deviceDataStore.edit { it[INSTALL_ID] = generated } }
        return DeviceRegistrationRequest(
            installId = installId,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
    companion object { private val INSTALL_ID = stringPreferencesKey("app_install_id") }
}
