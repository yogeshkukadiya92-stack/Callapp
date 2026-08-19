package com.callflow.app.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.FollowUpRecord
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.FollowUpRepository
import com.callflow.app.domain.repository.SyncRepository
import com.callflow.app.domain.repository.AuthRepository
import com.callflow.app.telecom.PermissionManager
import com.callflow.app.core.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class CallsViewModel @Inject constructor(calls: CallRepository) : ViewModel() {
    val calls = calls.observeRecentCalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
@HiltViewModel class FollowUpsViewModel @Inject constructor(private val repository: FollowUpRepository) : ViewModel() {
    val followUps = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun complete(id: String) { viewModelScope.launch { repository.complete(id) } }
}
data class PermissionSummary(val callTracking: PermissionState, val notifications: PermissionState, val calling: PermissionState)
@HiltViewModel class SyncStatusViewModel @Inject constructor(private val repository: SyncRepository, private val authRepository: AuthRepository, permissionManager: PermissionManager) : ViewModel() {
    val pending = repository.observePendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val conflicts = repository.observeConflictCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val permissions = PermissionSummary(permissionManager.callTrackingRole(), permissionManager.notifications(), permissionManager.callPermission())
    fun retry() { viewModelScope.launch { repository.syncPending() } }
    fun logout() { viewModelScope.launch { authRepository.logout() } }
}
