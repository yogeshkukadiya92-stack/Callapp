package com.callflow.app.data.remote

import com.callflow.app.core.model.DeviceStatus
import com.callflow.app.data.session.SessionTokenStore
import com.callflow.app.data.session.StoredSession
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthHttpTest {
    private lateinit var server: MockWebServer
    private lateinit var sessions: MemorySessionStore

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        sessions = MemorySessionStore(StoredSession("access-old", "refresh-old", "Agent", DeviceStatus.ACTIVE, "device-1"))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun access_token_is_attached_without_overwriting_an_explicit_header() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder().addInterceptor(AccessTokenInterceptor(sessions)).build()

        client.newCall(Request.Builder().url(server.url("/automatic")).build()).execute().close()
        client.newCall(Request.Builder().url(server.url("/explicit")).header("Authorization", "Bearer explicit").build()).execute().close()

        assertEquals("Bearer access-old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer explicit", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun unauthorized_response_rotates_tokens_and_replays_once() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))
        val refresh = FakeRefreshApi { TokenResponse("access-new", "refresh-new", "2026-08-19T12:00:00Z") }
        val client = OkHttpClient.Builder()
            .addInterceptor(AccessTokenInterceptor(sessions))
            .authenticator(RefreshTokenAuthenticator(refresh, sessions))
            .build()

        client.newCall(Request.Builder().url(server.url("/protected")).build()).execute().use { assertEquals(200, it.code) }

        assertEquals("refresh-old", refresh.receivedRefreshToken)
        assertEquals("access-new", sessions.current()?.accessToken)
        assertEquals("refresh-new", sessions.current()?.refreshToken)
        assertEquals("Bearer access-old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer access-new", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun rejected_refresh_clears_the_session_and_does_not_loop() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val refresh = FakeRefreshApi { throw HttpException(Response.error<TokenResponse>(401, "".toResponseBody())) }
        val client = OkHttpClient.Builder().addInterceptor(AccessTokenInterceptor(sessions)).authenticator(RefreshTokenAuthenticator(refresh, sessions)).build()

        client.newCall(Request.Builder().url(server.url("/expired")).build()).execute().use { assertEquals(401, it.code) }

        assertNull(sessions.current())
        assertEquals(1, server.requestCount)
    }

    @Test fun explicit_revocation_header_clears_the_session() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-Session-Revoked", "true"))
        val client = OkHttpClient.Builder().addInterceptor(SessionRevocationInterceptor(sessions)).build()

        client.newCall(Request.Builder().url(server.url("/revoked")).build()).execute().close()

        assertNull(sessions.current())
    }
}

private class MemorySessionStore(initial: StoredSession?) : SessionTokenStore {
    private var value = initial
    override suspend fun save(value: StoredSession) { this.value = value }
    override suspend fun clear() { value = null }
    override suspend fun current(): StoredSession? = value
}

private class FakeRefreshApi(private val result: suspend () -> TokenResponse) : RefreshTokenApi {
    var receivedRefreshToken: String? = null
    override suspend fun refresh(refreshToken: Map<String, String>): TokenResponse {
        receivedRefreshToken = refreshToken["refreshToken"]
        return result()
    }
}
