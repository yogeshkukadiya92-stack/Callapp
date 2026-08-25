package com.callflow.app.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.CallRecord
import com.callflow.app.core.model.SyncHealth
import com.callflow.app.core.model.FollowUpRecord
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.FollowUpRepository
import com.callflow.app.domain.repository.SyncRepository
import com.callflow.app.domain.repository.AuthRepository
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.core.model.Lead
import com.callflow.app.telecom.PermissionManager
import com.callflow.app.data.remote.AssignmentAvailabilityRequest
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.remote.ShiftSummaryResponse
import com.callflow.app.data.remote.LocationCheckInRequest
import com.callflow.app.location.LocationCapture
import com.callflow.app.core.model.PermissionState
import com.callflow.app.core.call.CallAnalysis
import com.callflow.app.core.call.CallAnalysisCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.callflow.app.data.local.CallFlowDao
import org.json.JSONArray

@HiltViewModel class CallsViewModel @Inject constructor(repository: CallRepository) : ViewModel() {
    val calls = repository.observeRecentCalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val analysis = calls.map { values: List<CallRecord> -> CallAnalysisCalculator.calculate(values) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallAnalysis())
}
data class TeamContentItem(val id: String, val title: String, val body: String, val category: String)
data class TeamContentUiState(val announcements: List<TeamContentItem> = emptyList(), val scripts: List<TeamContentItem> = emptyList())
@HiltViewModel class TeamContentViewModel @Inject constructor(dao: CallFlowDao, private val sync: SyncRepository) : ViewModel() {
    val state = dao.observeAppConfiguration().map { rows ->
        fun content(key: String): List<TeamContentItem> = runCatching {
            val array = JSONArray(rows.firstOrNull { it.key == key }?.value ?: "[]")
            buildList { for (index in 0 until array.length()) { val item = array.getJSONObject(index); if (item.optBoolean("active", true)) add(TeamContentItem(item.optString("id"), item.optString("title"), item.optString("body"), item.optString("category", "General"))) } }
        }.getOrDefault(emptyList())
        TeamContentUiState(content("team_announcements"), content("call_scripts"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamContentUiState())
    val refreshing = MutableStateFlow(false)
    fun refresh() { if (refreshing.value) return; viewModelScope.launch { refreshing.value = true; sync.syncPending(); refreshing.value = false } }
}
data class ReportsUiState(val calls: List<CallRecord> = emptyList(), val leads: List<Lead> = emptyList(), val followUps: List<FollowUpRecord> = emptyList())
@HiltViewModel class ReportsViewModel @Inject constructor(calls: CallRepository, leads: LeadRepository, followUps: FollowUpRepository, private val api: CallFlowApi) : ViewModel() {
    val state = combine(calls.observeRecentCalls(), leads.observeCallingQueue(), followUps.observeAll()) { callRows, leadRows, followUpRows -> ReportsUiState(callRows, leadRows, followUpRows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())
    val shiftSummary = MutableStateFlow<ShiftSummaryResponse?>(null)
    val shiftLoading = MutableStateFlow(true)
    val shiftError = MutableStateFlow<String?>(null)
    init { refreshShifts() }
    fun refreshShifts() { viewModelScope.launch { shiftLoading.value = true; shiftError.value = null; runCatching { api.shiftSummary() }.onSuccess { shiftSummary.value = it }.onFailure { shiftError.value = "Shift analytics are temporarily unavailable." }; shiftLoading.value = false } }
}
@HiltViewModel class FollowUpsViewModel @Inject constructor(private val repository: FollowUpRepository, leadsRepository: LeadRepository, private val api: CallFlowApi, private val location: LocationCapture) : ViewModel() {
    val followUps = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val leads = leadsRepository.observeCallingQueue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completingIds = MutableStateFlow<Set<String>>(emptySet())
    val completionError = MutableStateFlow<String?>(null)
    val checkedInIds = MutableStateFlow<Set<String>>(emptySet())
    fun complete(id: String) {
        if (id in completingIds.value) return
        viewModelScope.launch {
            completingIds.value += id; completionError.value = null
            repository.complete(id).onFailure { completionError.value = "We couldn’t complete this follow-up. Please try again." }
            completingIds.value -= id
        }
    }
    fun update(id: String, scheduledAt: java.time.Instant, note: String?) = mutate(id) { repository.update(id, scheduledAt, note) }
    fun cancel(id: String) = mutate(id) { repository.cancel(id) }
    fun checkIn(value: FollowUpRecord) {
        if (value.id in completingIds.value) return
        viewModelScope.launch {
            completingIds.value += value.id; completionError.value = null
            location.current().fold(onSuccess = { point ->
                runCatching { api.locationCheckIn(LocationCheckInRequest(value.id, value.leadId, point.latitude, point.longitude, point.accuracyMeters, point.capturedAt)) }
                    .onSuccess { checkedInIds.value += value.id }
                    .onFailure { completionError.value = "Meeting check-in could not be synced. Try again." }
            }, onFailure = { completionError.value = it.message ?: "Current location is unavailable." })
            completingIds.value -= value.id
        }
    }
    private fun mutate(id: String, operation: suspend () -> Result<Unit>) {
        if (id in completingIds.value) return
        viewModelScope.launch {
            completingIds.value += id; completionError.value = null
            operation().onFailure { completionError.value = it.message ?: "We couldn’t update this follow-up. Please try again." }
            completingIds.value -= id
        }
    }
}
data class PermissionSummary(val callTracking: PermissionState, val notifications: PermissionState, val calling: PermissionState)
data class AssignmentAvailabilityUiState(
    val acceptingLeads: Boolean? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)
@HiltViewModel class SyncStatusViewModel @Inject constructor(private val repository: SyncRepository, private val authRepository: AuthRepository, private val api: CallFlowApi, private val location: LocationCapture, permissionManager: PermissionManager) : ViewModel() {
    val pending = repository.observePendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val conflicts = repository.observeConflictCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val health = repository.observeHealth().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncHealth())
    val syncing = MutableStateFlow(false)
    val assignmentAvailability = MutableStateFlow(AssignmentAvailabilityUiState())
    val permissions = PermissionSummary(permissionManager.callTrackingRole(), permissionManager.notifications(), permissionManager.callPermission())
    fun hasLocationPermission() = location.hasPermission()
    init { refreshAssignmentAvailability() }
    fun refreshAssignmentAvailability() { viewModelScope.launch {
        assignmentAvailability.value = assignmentAvailability.value.copy(loading = true, error = null)
        runCatching { api.assignmentAvailability() }
            .onSuccess { assignmentAvailability.value = AssignmentAvailabilityUiState(acceptingLeads = it.acceptingLeads, loading = false) }
            .onFailure { assignmentAvailability.value = assignmentAvailability.value.copy(acceptingLeads = null, loading = false, error = "We couldn’t verify your assignment status. Check your connection and try again.") }
    } }
    fun setAcceptingLeads(accepting: Boolean) {
        if (assignmentAvailability.value.saving) return
        viewModelScope.launch {
            assignmentAvailability.value = assignmentAvailability.value.copy(saving = true, error = null)
            location.current().fold(onSuccess = { point -> runCatching { api.updateAssignmentAvailability(AssignmentAvailabilityRequest(accepting, point.latitude, point.longitude, point.accuracyMeters, point.capturedAt)) }
                .onSuccess { assignmentAvailability.value = AssignmentAvailabilityUiState(acceptingLeads = it.acceptingLeads, loading = false) }
                .onFailure { assignmentAvailability.value = assignmentAvailability.value.copy(saving = false, error = "Your status was not changed. Check your connection and try again.") }
            }, onFailure = { assignmentAvailability.value = assignmentAvailability.value.copy(saving = false, error = it.message ?: "Location is required to change shift status.") })
        }
    }
    fun retry() { if (syncing.value) return; viewModelScope.launch { syncing.value = true; repository.syncPending(); syncing.value = false } }
    fun logout() { viewModelScope.launch { authRepository.logout() } }
}
