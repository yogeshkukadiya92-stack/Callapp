package com.callflow.app.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrmEndpointTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() { context.getSharedPreferences("crm-endpoint", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun rejectsPlainHttpCredentialsAndQueryParameters() {
        val endpoint = CrmEndpoint(context)
        listOf("http://crm.example/api/", "https://user:pass@crm.example/api/", "https://crm.example/api/?key=secret").forEach {
            assertTrue(runCatching { endpoint.configure(it, "test") }.isFailure)
        }
    }
    @Test fun lockedWorkspaceCannotBeRepointed() {
        context.getSharedPreferences("crm-endpoint", Context.MODE_PRIVATE).edit().putBoolean("locked", true).commit()
        assertTrue(runCatching { CrmEndpoint(context).configure("https://other.example/api/", "other") }.isFailure)
    }
    @Test fun eachDashboardPersistsAndRoutesOnlyToItsOwnServer() {
        DashboardWorkspace.entries.forEach { workspace ->
            reset()
            val endpoint = CrmEndpoint(context)
            endpoint.configure(workspace)
            assertEquals(workspace, CrmEndpoint(context).workspace)
            var captured: okhttp3.Request? = null
            val client = okhttp3.OkHttpClient.Builder().addInterceptor(endpoint).addInterceptor { chain ->
                captured = chain.request()
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK").body(okhttp3.ResponseBody.create(null, "{}" )).build()
            }.build()
            client.newCall(okhttp3.Request.Builder().url(com.callflow.app.BuildConfig.API_BASE_URL + "sync/changes?cursor=123").build()).execute().close()
            assertEquals(workspace.baseUrl + "sync/changes?cursor=123", captured!!.url.toString())
            assertEquals(workspace.connectorId, captured!!.header("X-CallFlow-Connector"))
            assertTrue(endpoint.locked)
            assertTrue(runCatching { endpoint.configure(DashboardWorkspace.entries.first { it != workspace }) }.isFailure)
        }
    }
}
