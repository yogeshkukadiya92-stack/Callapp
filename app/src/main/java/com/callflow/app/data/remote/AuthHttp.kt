package com.callflow.app.data.remote

import com.callflow.app.data.session.SessionTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class AccessTokenInterceptor @Inject constructor(private val sessions: SessionTokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)
        val token = runBlocking { sessions.current()?.accessToken }
        return chain.proceed(if (token.isNullOrBlank()) request else request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}

@Singleton
class RefreshTokenAuthenticator @Inject constructor(private val refreshApi: RefreshTokenApi, private val sessions: SessionTokenStore) : Authenticator {
    private val lock = Any()
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.responseCount() >= 2) return null
        return synchronized(lock) {
            val current = runBlocking { sessions.current() } ?: return@synchronized null
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (requestToken != null && requestToken != current.accessToken) return@synchronized response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            val refreshResult = runBlocking { runCatching { refreshApi.refresh(mapOf("refreshToken" to current.refreshToken)) } }
            val refreshed = refreshResult.getOrNull()
            if (refreshed == null) {
                val failure = refreshResult.exceptionOrNull()
                if (failure is HttpException && failure.code() in setOf(400, 401, 403)) runBlocking { sessions.clear() }
                null
            } else {
                runBlocking { sessions.save(current.copy(accessToken = refreshed.accessToken, refreshToken = refreshed.refreshToken)) }
                response.request.newBuilder().header("Authorization", "Bearer ${refreshed.accessToken}").build()
            }
        }
    }
}

@Singleton
class SessionRevocationInterceptor @Inject constructor(private val sessions: SessionTokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 403 && response.header("X-Session-Revoked").equals("true", ignoreCase = true)) runBlocking { sessions.clear() }
        return response
    }
}

private fun Response.responseCount(): Int {
    var count = 1; var prior = priorResponse
    while (prior != null) { count++; prior = prior.priorResponse }
    return count
}
