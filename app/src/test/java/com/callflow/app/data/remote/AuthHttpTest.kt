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
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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

    @Test fun login_request_is_serialized_and_token_response_is_deserialized() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"accessToken":"access-new","refreshToken":"refresh-new","expiresAt":"2026-08-19T12:00:00Z"}"""),
        )
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(CallFlowApi::class.java)

        val token = api.login(LoginRequest(identity = "9825344428", password = "secret"))

        assertEquals("access-new", token.accessToken)
        assertEquals(
            "{\"identity\":\"9825344428\",\"password\":\"secret\"}",
            server.takeRequest().body.readUtf8(),
        )
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

    @Test fun logout_during_refresh_does_not_restore_the_cleared_session() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val refreshStarted = CountDownLatch(1)
        val allowRefreshToFinish = CountDownLatch(1)
        val refresh = FakeRefreshApi {
            refreshStarted.countDown()
            check(allowRefreshToFinish.await(2, TimeUnit.SECONDS))
            TokenResponse("access-new", "refresh-new", "2026-08-19T12:00:00Z")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AccessTokenInterceptor(sessions))
            .authenticator(RefreshTokenAuthenticator(refresh, sessions))
            .build()
        val call = client.newCall(Request.Builder().url(server.url("/protected")).build())
        val requestThread = Thread { call.execute().close() }.apply { start() }

        check(refreshStarted.await(2, TimeUnit.SECONDS))
        sessions.clear()
        allowRefreshToFinish.countDown()
        requestThread.join(2_000)

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
    private val lock = Any()
    private var value = initial
    override suspend fun save(value: StoredSession) = synchronized(lock) { this.value = value }
    override suspend fun saveIfCurrent(expectedRefreshToken: String, value: StoredSession): Boolean = synchronized(lock) {
        if (this.value?.refreshToken != expectedRefreshToken) return@synchronized false
        this.value = value
        true
    }
    override suspend fun clear() = synchronized(lock) { value = null }
    override suspend fun current(): StoredSession? = synchronized(lock) { value }
}

private class FakeRefreshApi(private val result: suspend () -> TokenResponse) : RefreshTokenApi {
    var receivedRefreshToken: String? = null
    override suspend fun refresh(refreshToken: Map<String, String>): TokenResponse {
        receivedRefreshToken = refreshToken["refreshToken"]
        return result()
    }
}
