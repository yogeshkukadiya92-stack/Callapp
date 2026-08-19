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
        val current = state.value; state.value = current.copy(loading = true, error = null)
        viewModelScope.launch { repository.login(current.identity, current.password).onFailure { state.value = state.value.copy(loading = false, error = it.message ?: "Sign in failed") } }
    }
}
