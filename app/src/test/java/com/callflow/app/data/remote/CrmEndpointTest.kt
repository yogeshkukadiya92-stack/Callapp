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
}
