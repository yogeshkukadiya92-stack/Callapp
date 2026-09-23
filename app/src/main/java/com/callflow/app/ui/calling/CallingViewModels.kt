package com.callflow.app.ui.calling

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.DispositionInput
import com.callflow.app.core.model.DispositionOption
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.Outcome
import com.callflow.app.core.model.PermissionState
import com.callflow.app.data.repository.OfflineCallRepository
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.telecom.CallIntegrationManager
import com.callflow.app.telecom.CallIntegrationState
import com.callflow.app.telecom.CallingAccount
import com.callflow.app.telecom.PermissionManager
import com.callflow.app.telecom.CallUiController
import com.callflow.app.core.contacts.DeviceContactResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.callflow.app.domain.usecase.PrioritizeCallingQueue
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CallingUiState(val lead: Lead? = null, val integrationState: CallIntegrationState = CallIntegrationState.ManualMode, val callLogPermission: PermissionState = PermissionState.DENIED, val callingAccounts: List<CallingAccount> = emptyList(), val selectedAccountId: String? = null, val callId: String? = null, val activeCall: com.callflow.app.core.model.CallRecord? = null, val subscriptionReadOnly: Boolean = false, val error: String? = null)

data class ManualDialUiState(
    val number: String = "",
    val matchedLead: Lead? = null,
    val matchedContactName: String? = null,
    val integrationState: CallIntegrationState = CallIntegrationState.ManualMode,
    val error: String? = null,
    val message: String? = null,
    val callingAccounts: List<CallingAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val subscriptionReadOnly: Boolean = false,
)

@HiltViewModel
class ManualDialViewModel @Inject constructor(
    private val leads: LeadRepository,
    private val integration: CallIntegrationManager,
    private val permissions: PermissionManager,
    dao: CallFlowDao,
    private val controller: CallUiController,
    private val contacts: DeviceContactResolver,
    simPreferenceStore: com.callflow.app.data.session.SimPreferenceStore? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManualDialUiState(integrationState = integration.state(), callingAccounts = integration.callingAccounts()))
    private var numberLookup: Job? = null
    val state: StateFlow<ManualDialUiState> = mutable
    init {
        viewModelScope.launch { dao.observeAppConfiguration().collect { rows -> mutable.value=mutable.value.copy(subscriptionReadOnly=rows.any { it.key=="subscription_access"&&it.value.contains("\"mode\":\"READ_ONLY\"") }) } }
        if (simPreferenceStore != null) {
            viewModelScope.launch {
                simPreferenceStore.selectedSimSlot.collect { slot ->
                    if (slot in 1..2) {
                        val matchingId = mutable.value.callingAccounts.firstOrNull { it.slotIndex == slot }?.id
                        if (matchingId != null) selectAccount(matchingId)
                    }
                }
            }
        }
    }

    fun updateNumber(value: String) {
        val cleaned = value.filter { it.isDigit() || it == '+' }.take(16)
        mutable.value = mutable.value.copy(number = cleaned, matchedLead = null, matchedContactName = null, error = null, message = null)
        numberLookup?.cancel()
        if (cleaned.count(Char::isDigit) >= 7) numberLookup = viewModelScope.launch {
            delay(180)
            val (match, contactName) = withContext(Dispatchers.IO) {
                val foundLead = leads.findByPhone(cleaned)
                foundLead to if (foundLead == null) contacts.resolveContactName(cleaned) else null
            }
            if (mutable.value.number == cleaned) mutable.value = mutable.value.copy(matchedLead = match, matchedContactName = contactName)
        }
    }

    fun append(value: String) = updateNumber(state.value.number + value)
    fun backspace() = updateNumber(state.value.number.dropLast(1))
    fun clearNumber() = updateNumber("")
    fun selectAccount(id: String?) { mutable.value = mutable.value.copy(selectedAccountId = id) }
    fun hasDirectCallPermission(): Boolean = permissions.callPermission() == PermissionState.GRANTED

    fun callUnknown() {
        val current = state.value
        if (current.subscriptionReadOnly) { mutable.value=current.copy(error="Subscription expired. Calling is read-only until renewal.");return }
        if (current.number.count(Char::isDigit) < 7) {
            mutable.value = current.copy(error = "Enter a valid phone number")
            return
        }
        if (current.matchedLead != null) {
            mutable.value = current.copy(error = "Use the assigned lead call flow for this number")
            return
        }
        if (mutable.value.message == "Starting call…") return
        mutable.value = current.copy(message = "Starting call…", error = null)
        viewModelScope.launch {
            val contactName = withContext(Dispatchers.IO) { contacts.resolveContactName(current.number) }
            controller.setPreferredCaller(current.number, contactName)
            when (integration.initiateCall(current.number, current.selectedAccountId)) {
                is Outcome.Success -> mutable.value = mutable.value.copy(message = "Call started. Only an actual call will be added to history.", error = null)
                is Outcome.Failure -> mutable.value = mutable.value.copy(message = null, error = "Could not start the call")
            }
        }
    }
}

@HiltViewModel
class CallingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val leads: LeadRepository,
    private val calls: CallRepository,
    private val integration: CallIntegrationManager,
    private val permissions: PermissionManager,
    dao: CallFlowDao,
    private val controller: CallUiController,
    simPreferenceStore: com.callflow.app.data.session.SimPreferenceStore? = null,
) : ViewModel() {
    private val leadId: String = checkNotNull(savedStateHandle["leadId"])
    private val mutable = MutableStateFlow(CallingUiState(integrationState = integration.state(), callLogPermission = permissions.callLogPermission(), callingAccounts = integration.callingAccounts()))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val activeCall = mutable.map { it.callId }.distinctUntilChanged().flatMapLatest { id -> id?.let(calls::observeCall) ?: flowOf(null) }
    private val subscriptionReadOnly=dao.observeAppConfiguration().map { rows->rows.any { it.key=="subscription_access"&&it.value.contains("\"mode\":\"READ_ONLY\"") } }
    val state: StateFlow<CallingUiState> = combine(leads.observeLead(leadId), mutable, activeCall,subscriptionReadOnly) { lead, local, currentCall,readOnly -> local.copy(lead = lead, activeCall = currentCall,subscriptionReadOnly=readOnly) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    init {
        if (simPreferenceStore != null) {
            viewModelScope.launch {
                simPreferenceStore.selectedSimSlot.collect { slot ->
                    if (slot in 1..2) {
                        val matchingId = mutable.value.callingAccounts.firstOrNull { it.slotIndex == slot }?.id
                        if (matchingId != null) selectAccount(matchingId)
                    }
                }
            }
        }
    }
    fun roleIntent() = integration.roleRequestIntent()
    fun refreshRole() { mutable.value = mutable.value.copy(integrationState = integration.state()) }
    fun refreshCallLogPermission() {
        mutable.value = mutable.value.copy(callLogPermission = permissions.callLogPermission())
    }
    fun hasDirectCallPermission(): Boolean = permissions.callPermission() == PermissionState.GRANTED
    fun selectAccount(id: String?) { mutable.value = mutable.value.copy(selectedAccountId = id) }
    fun call(onStarted: (String) -> Unit) {
        val lead = state.value.lead ?: return
        if(state.value.subscriptionReadOnly){mutable.value=mutable.value.copy(error="Subscription expired. Calling is read-only until renewal.");return}
        if (lead.doNotCall) {
            mutable.value = mutable.value.copy(error = "Call blocked: this lead is marked Do Not Call.")
            return
        }
        controller.setPreferredCaller(lead.displayPhone, lead.name)
        if (integration.state() != CallIntegrationState.Ready) {
            when (integration.initiateCall(lead.displayPhone, state.value.selectedAccountId)) {
                is Outcome.Success -> mutable.value = mutable.value.copy(error = "Call log will be added only after an actual call is made.")
                is Outcome.Failure -> mutable.value = mutable.value.copy(error = "Could not open the phone app.")
            }
            return
        }
        viewModelScope.launch { calls.startOutgoingCall(lead).fold(onSuccess = { id ->
            when (integration.initiateCall(lead.displayPhone, state.value.selectedAccountId)) {
                is Outcome.Success -> { mutable.value = mutable.value.copy(callId = id); onStarted(id) }
                is Outcome.Failure -> mutable.value = mutable.value.copy(error = "Could not start the call.")
            }
        }, onFailure = { mutable.value = mutable.value.copy(error = "Could not prepare this call") }) }
    }
}

data class DispositionUiState(
    val lead: Lead? = null,
    val options: List<DispositionOption> = emptyList(),
    val selected: DispositionOption? = null,
    val note: String = "",
    val followUpAt: Instant? = null,
    val meetingLink: String = "",
    val addToCalendar: Boolean = true,
    val noteTemplates: List<String> = defaultNoteTemplates,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DispositionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calls: CallRepository,
    private val offlineCalls: OfflineCallRepository,
    private val leads: LeadRepository,
    private val prioritize: PrioritizeCallingQueue,
    private val postCall: com.callflow.app.telecom.PostCallCoordinator,
    private val api: CallFlowApi,
) : ViewModel() {
    private val callId: String = checkNotNull(savedStateHandle["callId"])
    private val leadId: String = checkNotNull(savedStateHandle["leadId"])
    private val mutable = MutableStateFlow(DispositionUiState())
    val state = combine(leads.observeLead(leadId), calls.observeDispositions(), mutable) { lead, options, local -> local.copy(lead = lead, options = options) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    init {
        viewModelScope.launch { offlineCalls.seedDispositionsIfEmpty() }
        viewModelScope.launch {
            runCatching { api.engagementConfig().noteTemplates }
                .onSuccess { templates -> if (templates.isNotEmpty()) mutable.value = mutable.value.copy(noteTemplates = templates.take(20)) }
        }
    }
    fun select(value: DispositionOption) { mutable.value = mutable.value.copy(selected = value, error = null) }
    fun note(value: String) { if (value.length <= 500) mutable.value = mutable.value.copy(note = value, error = null) }
    fun addSuggestion(value: String) { note(listOf(state.value.note, value).filter { it.isNotBlank() }.joinToString(" · ")) }
    fun meetingLink(value: String) { if (value.length <= 300) mutable.value = mutable.value.copy(meetingLink = value, error = null) }
    fun addToCalendar(value: Boolean) { mutable.value = mutable.value.copy(addToCalendar = value) }
    fun schedule(secondsFromNow: Long) { mutable.value = mutable.value.copy(followUpAt = Instant.now().plusSeconds(secondsFromNow)) }
    fun scheduleNextMonday() {
        val next = java.time.ZonedDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(10).withMinute(0).withSecond(0).withNano(0)
        mutable.value = mutable.value.copy(followUpAt = next.toInstant(), error = null)
    }
    fun scheduleAt(value: Instant) { mutable.value = mutable.value.copy(followUpAt = value, error = null) }
    fun save(onSaved: () -> Unit) {
        val current = state.value
        if (current.saving) return
        val selected = current.selected ?: return mutable.updateError("Choose a call result")
        val meetingLink = current.meetingLink.trim()
        if (meetingLink.isNotEmpty() && !meetingLink.isSupportedMeetingLink()) return mutable.updateError("Enter a valid Google Meet or Zoom https link")
        mutable.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            calls.saveDisposition(DispositionInput(callId, leadId, selected, current.note, current.followUpAt, meetingLink.ifBlank { null })).fold(
                onSuccess = { postCall.complete(callId); onSaved() },
                onFailure = { mutable.updateError(it.message ?: "Could not save the result") },
            )
        }
    }
    fun saveNext(onSaved: (String?) -> Unit) {
        save {
            viewModelScope.launch {
                val next = prioritize(leads.observeCallingQueue().first()).firstOrNull { it.id != leadId && !it.doNotCall }
                onSaved(next?.id)
            }
        }
    }
    private fun MutableStateFlow<DispositionUiState>.updateError(message: String) { value = value.copy(saving = false, error = message) }
}

internal val defaultNoteTemplates = listOf("No answer", "Call back tomorrow", "Interested", "Price shared", "Meeting booked", "Not eligible", "Wrong number")

internal fun String.isSupportedMeetingLink(): Boolean = runCatching {
    val uri = java.net.URI(trim())
    val host = uri.host?.lowercase().orEmpty()
    uri.scheme.equals("https", true) && (host == "meet.google.com" || host == "zoom.us" || host.endsWith(".zoom.us"))
}.getOrDefault(false)
