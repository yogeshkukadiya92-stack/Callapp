package com.callflow.app.data.remote

import com.callflow.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URI

class LiveEndpointConfigurationTest {
    @Test
    fun coachForLifeBuildUsesCallFlowApiPrefix() {
        assertFalse("Normal builds must never enable demo login/data", BuildConfig.USE_FAKE_BACKEND)
        val endpoint = URI(BuildConfig.API_BASE_URL)
        assertEquals("dashboard.coachforlife.in", endpoint.host)
        assertEquals("/api/callflow/", endpoint.path)
    }
}
