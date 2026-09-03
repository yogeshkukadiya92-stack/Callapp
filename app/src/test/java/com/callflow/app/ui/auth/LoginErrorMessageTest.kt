package com.callflow.app.ui.auth

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LoginErrorMessageTest {
    @Test fun unauthorizedLoginShowsCredentialGuidanceInsteadOfHttpCode() {
        val error = HttpException(Response.error<Unit>(401, "".toResponseBody()))
        assertEquals("Mobile/email or password is incorrect.", loginErrorMessage(error))
    }

    @Test fun offlineLoginShowsConnectionGuidance() {
        assertEquals("Could not connect. Check your internet and try again.", loginErrorMessage(IOException("timeout")))
    }
}
