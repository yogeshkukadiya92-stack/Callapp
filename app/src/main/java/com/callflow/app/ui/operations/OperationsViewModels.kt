package com.callflow.app.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
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
import com.callflow.app.telecom.CallIntegrationManager
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import com.callflow.app.data.local.CallFlowDao
import org.json.JSONArray
import com.callflow.app.core.model.TimelineItem
import com.callflow.app.telecom.PostCallCoordinator
import com.callflow.app.telecom.PostCallTarget

@HiltViewModel class CallsViewModel @Inject constructor(
    repository: CallRepository,
    leads: LeadRepository,
    private val contacts: com.callflow.app.core.contacts.DeviceContactResolver,
    private val callLogImporter: com.callflow.app.telecom.CallLogImporter,
    @ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {
    private val refreshInProgress = MutableStateFlow(false)
    val refreshingCallHistory = refreshInProgress
    val calls = repository.observeRecentCalls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val leadNames = leads.observeAllAssignedLeads().map { values -> values.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val contactNames = calls.map { callList ->
        callList.take(40).map { it.phone }.distinct().mapNotNull { phone ->
            contacts.resolveContactName(phone)?.let { name -> phone to name }
        }.toMap()
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val analysis = calls.map { values: List<CallRecord> -> CallAnalysisCalculator.calculate(values) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallAnalysis())

    fun refreshCallHistory() {
        if (refreshInProgress.value) return
        viewModelScope.launch(Dispatchers.IO) {
            refreshInProgress.value = true
            try {
                if (callLogImporter.importNewCalls() > 0) {
                    com.callflow.app.sync.SyncWorker.syncNow(appContext)
                }
            } finally {
                refreshInProgress.value = false
            }
        }
    }

    fun syncImportedCalls() {
        com.callflow.app.sync.SyncWorker.syncNow(appContext)
    }
}

@HiltViewModel class PostCallNavigationViewModel @Inject constructor(private val coordinator: PostCallCoordinator) : ViewModel() {
    val target = coordinator.target
    fun consume(value: PostCallTarget) = coordinator.consume(value)
}

data class CallDetailsUiState(
    val loading: Boolean = true,
    val call: CallRecord? = null,
    val lead: Lead? = null,
    val notes: List<TimelineItem> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
    val contactName: String? = null,
)

@HiltViewModel
class CallDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calls: CallRepository,
    leads: LeadRepository,
    dao: CallFlowDao,
    private val postCall: PostCallCoordinator,
    private val contacts: com.callflow.app.core.contacts.DeviceContactResolver,
) : ViewModel() {
    private val callId: String = checkNotNull(savedStateHandle["callId"])
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val call = calls.observeCall(callId)
    private val notes = dao.observeCallNotes(callId).map { values -> values.map { TimelineItem(it.id, "NOTE", java.time.Instant.ofEpochMilli(it.createdAt), "Call note", it.body) } }
    private val lead = combine(call, leads.observeAllAssignedLeads()) { value, allLeads -> allLeads.firstOrNull { it.id == value?.leadId } }
    private val contactName = call.map { value ->
        kotlinx.coroutines.withContext(Dispatchers.IO) { value?.phone?.let(contacts::resolveContactName) }
    }
    val state = combine(call, lead, contactName, notes, saving) { callValue, leadValue, contactValue, noteValues, isSaving ->
        CallDetailsUiState(false, callValue, leadValue, noteValues, isSaving, message.value, contactValue)
    }.combine(message) { current, msg ->
        current.copy(message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallDetailsUiState())

    fun addNote(body: String, onSaved: () -> Unit = {}) {
        val current = state.value
        val leadId = current.call?.leadId
        if (current.call == null) return
        if (saving.value) return
        viewModelScope.launch {
            saving.value = true; message.value = null
            calls.addCallNote(callId, leadId, body)
                .onSuccess { postCall.complete(callId); message.value = if (leadId == null) "Saved on this device. Dashboard sync starts after lead assignment." else "Saved and queued for dashboard sync"; onSaved() }
                .onFailure { message.value = it.message ?: "The note could not be saved." }
            saving.value = false
        }
    }

    fun saveResult(status: com.callflow.app.core.model.DispositionOption, body: String, followUpAt: java.time.Instant?, onSaved: () -> Unit) {
        val current = state.value.call ?: return
        if (saving.value) return
        if (followUpAt != null && !followUpAt.isAfter(java.time.Instant.now())) {
            message.value = "Choose a future follow-up date and time"
            return
        }
        if (current.leadId == null) {
            val summary = listOfNotNull("Status: ${status.name}", followUpAt?.let { "Next follow-up: $it" }, body.trim().takeIf { it.isNotEmpty() }).joinToString("\n")
            addNote(summary, onSaved)
            return
        }
        saving.value = true
        message.value = null
        viewModelScope.launch {
            calls.saveDisposition(com.callflow.app.core.model.DispositionInput(callId, current.leadId, status, body, followUpAt))
                .onSuccess { postCall.complete(callId); onSaved() }
                .onFailure { message.value = it.message ?: "Could not save call details" }
            saving.value = false
        }
    }

    fun clearMessage() { message.value = null }
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
data class ReportsUiState(val calls: List<CallRecord> = emptyList(), val leads: List<Lead> = emptyList(), val followUps: List<FollowUpRecord> = emptyList(),val exportAllowed:Boolean=true)
@HiltViewModel class ReportsViewModel @Inject constructor(calls: CallRepository, leads: LeadRepository, followUps: FollowUpRepository, private val api: CallFlowApi,dao:CallFlowDao) : ViewModel() {
    val state = combine(calls.observeRecentCalls(), leads.observeAllAssignedLeads(), followUps.observeAll(),dao.observeAppConfiguration()) { callRows, leadRows, followUpRows,configuration -> ReportsUiState(callRows, leadRows, followUpRows,!configuration.any { it.key=="subscription_access"&&it.value.contains("\"exportAllowed\":false") }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())
    val shiftSummary = MutableStateFlow<ShiftSummaryResponse?>(null)
    val shiftLoading = MutableStateFlow(true)
    val shiftError = MutableStateFlow<String?>(null)
    init { refreshShifts() }
    fun refreshShifts() { viewModelScope.launch { shiftLoading.value = true; shiftError.value = null; runCatching { api.shiftSummary() }.onSuccess { shiftSummary.value = it }.onFailure { shiftError.value = "Shift analytics are temporarily unavailable." }; shiftLoading.value = false } }
}
@HiltViewModel class FollowUpsViewModel @Inject constructor(private val repository: FollowUpRepository, leadsRepository: LeadRepository, private val api: CallFlowApi, private val location: LocationCapture) : ViewModel() {
    val followUps = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val leads = leadsRepository.observeAllAssignedLeads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
data class PermissionSummary(val callTracking: PermissionState, val notifications: PermissionState, val calling: PermissionState, val callLog: PermissionState)
data class SetupReadiness(val ready: Boolean, val title: String, val action: String)

internal fun setupReadiness(permissions: PermissionSummary, failed: Int, conflicts: Int): SetupReadiness = when {
    permissions.callLog != PermissionState.GRANTED -> SetupReadiness(false, "Call history permission required", "Allow call history so completed calls, SIM and duration can be synced.")
    failed > 0 -> SetupReadiness(false, "$failed records need sync retry", "Connect to the internet and use Sync now. Local records remain safely stored.")
    conflicts > 0 -> SetupReadiness(false, "$conflicts sync conflicts detected", "Keep the app online and contact support if conflicts remain after sync.")
    permissions.notifications == PermissionState.DENIED || permissions.notifications == PermissionState.PERMANENTLY_DENIED -> SetupReadiness(true, "Calling is ready", "Enable notifications to receive due follow-up reminders.")
    else -> SetupReadiness(true, "CallFlow is production ready", "Native phone calling, SIM filtering and automatic dashboard sync are active.")
}
data class AssignmentAvailabilityUiState(
    val acceptingLeads: Boolean? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)
@HiltViewModel class SyncStatusViewModel @Inject constructor(
    private val repository: SyncRepository,
    private val authRepository: AuthRepository,
    private val api: CallFlowApi,
    private val location: LocationCapture,
    private val permissionManager: PermissionManager,
    private val callIntegration: CallIntegrationManager,
    private val dao: CallFlowDao,
    private val simPreferenceStore: com.callflow.app.data.session.SimPreferenceStore,
) : ViewModel() {
    val pending = repository.observePendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val conflicts = repository.observeConflictCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val health = repository.observeHealth().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncHealth())
    val syncBreakdown = dao.observeSyncQueueBreakdown().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val syncing = MutableStateFlow(false)
    val assignmentAvailability = MutableStateFlow(AssignmentAvailabilityUiState())
    val permissions = MutableStateFlow(readPermissions())
    val selectedSimSlot = simPreferenceStore.selectedSimSlot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val callingAccounts = MutableStateFlow(callIntegration.callingAccounts())

    fun roleIntent() = callIntegration.roleRequestIntent()
    fun refreshPermissions() {
        permissions.value = readPermissions()
        callingAccounts.value = callIntegration.callingAccounts()
    }
    fun selectSimSlot(slot: Int, subscriptionId: String? = null, label: String? = null) {
        viewModelScope.launch {
            simPreferenceStore.setSimSyncSlot(slot, subscriptionId, label)
            if (slot in 1..2) {
                dao.purgePendingCallSyncEventsForOtherSims(slot)
            }
        }
    }
    private fun readPermissions() = PermissionSummary(permissionManager.callTrackingRole(), permissionManager.notifications(), permissionManager.callPermission(), permissionManager.callLogPermission())
    fun hasLocationPermission() = location.hasPermission()
    init {
        refreshAssignmentAvailability()
        callingAccounts.value = callIntegration.callingAccounts()
    }
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
