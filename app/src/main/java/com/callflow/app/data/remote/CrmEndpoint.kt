package com.callflow.app.data.remote

import android.content.Context
import com.callflow.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrmEndpoint @Inject constructor(@ApplicationContext context: Context) : Interceptor {
    private val prefs = context.getSharedPreferences("crm-endpoint", Context.MODE_PRIVATE)
    init {
        // Existing installations keep their original CRM, including after logout.
        if (!prefs.contains("url") && java.io.File(context.filesDir, "datastore/secure_session.preferences_pb").exists()) {
            prefs.edit().putBoolean("locked", true).commit()
        }
    }
    val url: String get() = prefs.getString("url", BuildConfig.API_BASE_URL)!!
    val connector: String get() = prefs.getString("connector", BuildConfig.DASHBOARD_CONNECTOR_ID)!!
    val locked: Boolean get() = prefs.getBoolean("locked", false)
    @Synchronized fun configure(value: String, id: String) {
        check(!locked) { "CRM is locked after first connection to protect local records. Contact support to migrate." }
        val parsed = value.trim().toHttpUrl()
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) { "Use a HTTPS base URL without credentials, query or fragment." }
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "Connector ID must use letters, digits, - or _." }
        check(prefs.edit().putString("url", parsed.toString().trimEnd('/') + "/").putString("connector", id).commit())
    }
    @Synchronized private fun destination(): Pair<String, String> {
        check(prefs.edit().putBoolean("locked", true).commit())
        return url to connector
    }
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val defaultBase = BuildConfig.API_BASE_URL.toHttpUrl()
        val (base, id) = destination()
        val relative = original.url.encodedPath.removePrefix(defaultBase.encodedPath)
        val target = base.toHttpUrl().newBuilder().addEncodedPathSegments(relative).encodedQuery(original.url.encodedQuery).build()
        return chain.proceed(original.newBuilder().url(target).header("X-CallFlow-Connector", id).build())
    }
}
