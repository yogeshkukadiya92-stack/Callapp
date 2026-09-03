package com.callflow.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.SessionState
import com.callflow.app.domain.repository.AuthRepository
import com.callflow.app.data.session.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.IOException
import retrofit2.HttpException

@HiltViewModel
class AppSessionViewModel @Inject constructor(private val repository: AuthRepository, private val onboardingStore: OnboardingStore) : ViewModel() {
    val session: StateFlow<SessionState> = repository.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
    fun logout() { viewModelScope.launch { repository.logout() } }
    val checkingDevice = MutableStateFlow(false)
    val deviceError = MutableStateFlow<String?>(null)
    val onboardingComplete = onboardingStore.completed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun completeOnboarding() { viewModelScope.launch { onboardingStore.complete() } }
    fun checkDevice() {
        checkingDevice.value = true; deviceError.value = null
        viewModelScope.launch { repository.refreshDeviceStatus().onFailure { deviceError.value = it.message ?: "Could not check approval" }; checkingDevice.value = false }
    }
}

data class LoginUiState(val identity: String = "", val password: String = "", val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    val state = MutableStateFlow(LoginUiState())
    fun identity(value: String) { state.value = state.value.copy(identity = value, error = null) }
    fun password(value: String) { state.value = state.value.copy(password = value, error = null) }
    fun login() {
        val current = state.value
        if (current.loading) return
        if (current.identity.isBlank()) { state.value = current.copy(error = "Enter your Sales Access mobile number or email."); return }
        if (current.password.length < 4) { state.value = current.copy(error = "Enter your password (minimum 4 characters)."); return }
        state.value = current.copy(loading = true, error = null)
        viewModelScope.launch { repository.login(current.identity.trim(), current.password).onFailure { state.value = state.value.copy(loading = false, error = loginErrorMessage(it)) } }
    }
}

internal fun loginErrorMessage(error: Throwable): String = when (error) {
    is HttpException -> when (error.code()) {
        400 -> "Please check the mobile/email and password format."
        401 -> "Mobile/email or password is incorrect."
        403 -> "Your Sales Access account or this device is not approved."
        404 -> "CallFlow login service is unavailable. Please contact support."
        429 -> "Too many sign-in attempts. Please wait and try again."
        in 500..599 -> "Coach For Life server is temporarily unavailable. Please try again."
        else -> "Sign in could not be completed. Please try again."
    }
    is IOException -> "Could not connect. Check your internet and try again."
    is IllegalArgumentException -> error.message ?: "Check the sign-in details and try again."
    else -> "Sign in could not be completed. Please try again."
}
