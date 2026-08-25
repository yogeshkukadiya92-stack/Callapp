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
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.telecom.CallIntegrationManager
import com.callflow.app.telecom.CallIntegrationState
import com.callflow.app.telecom.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CallingUiState(val lead: Lead? = null, val integrationState: CallIntegrationState = CallIntegrationState.ManualMode, val callLogPermission: PermissionState = PermissionState.DENIED, val callId: String? = null, val error: String? = null)

@HiltViewModel
class CallingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val leads: LeadRepository,
    private val calls: CallRepository,
    private val integration: CallIntegrationManager,
    private val permissions: PermissionManager,
) : ViewModel() {
    private val leadId: String = checkNotNull(savedStateHandle["leadId"])
    private val mutable = MutableStateFlow(CallingUiState(integrationState = integration.state(), callLogPermission = permissions.callLogPermission()))
    val state: StateFlow<CallingUiState> = combine(leads.observeLead(leadId), mutable) { lead, local -> local.copy(lead = lead) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    fun roleIntent() = integration.roleRequestIntent()
    fun refreshRole() { mutable.value = mutable.value.copy(integrationState = integration.state()) }
    fun refreshCallLogPermission() {
        mutable.value = mutable.value.copy(callLogPermission = permissions.callLogPermission())
    }
    fun hasDirectCallPermission(): Boolean = permissions.callPermission() == PermissionState.GRANTED
    fun call(onStarted: (String) -> Unit) {
        val lead = state.value.lead ?: return
        if (lead.doNotCall) {
            mutable.value = mutable.value.copy(error = "Call blocked: this lead is marked Do Not Call.")
            return
        }
        if (integration.state() != CallIntegrationState.Ready) {
            when (integration.initiateCall(lead.displayPhone)) {
                is Outcome.Success -> mutable.value = mutable.value.copy(error = "Call log will be added only after an actual call is made.")
                is Outcome.Failure -> mutable.value = mutable.value.copy(error = "Could not open the phone app.")
            }
            return
        }
        viewModelScope.launch { calls.startOutgoingCall(lead).fold(onSuccess = { id ->
            when (integration.initiateCall(lead.displayPhone)) {
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
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DispositionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val calls: CallRepository,
    private val offlineCalls: OfflineCallRepository,
    leads: LeadRepository,
) : ViewModel() {
    private val callId: String = checkNotNull(savedStateHandle["callId"])
    private val leadId: String = checkNotNull(savedStateHandle["leadId"])
    private val mutable = MutableStateFlow(DispositionUiState())
    val state = combine(leads.observeLead(leadId), calls.observeDispositions(), mutable) { lead, options, local -> local.copy(lead = lead, options = options) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    init { viewModelScope.launch { offlineCalls.seedDispositionsIfEmpty() } }
    fun select(value: DispositionOption) { mutable.value = mutable.value.copy(selected = value, error = null) }
    fun note(value: String) { if (value.length <= 500) mutable.value = mutable.value.copy(note = value, error = null) }
    fun addSuggestion(value: String) { note(listOf(state.value.note, value).filter { it.isNotBlank() }.joinToString(" · ")) }
    fun schedule(secondsFromNow: Long) { mutable.value = mutable.value.copy(followUpAt = Instant.now().plusSeconds(secondsFromNow)) }
    fun scheduleNextMonday() {
        val next = java.time.ZonedDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(10).withMinute(0).withSecond(0).withNano(0)
        mutable.value = mutable.value.copy(followUpAt = next.toInstant(), error = null)
    }
    fun scheduleAt(value: Instant) { mutable.value = mutable.value.copy(followUpAt = value, error = null) }
    fun save(onSaved: () -> Unit) {
        val current = state.value
        val selected = current.selected ?: return mutable.updateError("Choose a call result")
        mutable.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            calls.saveDisposition(DispositionInput(callId, leadId, selected, current.note, current.followUpAt)).fold(
                onSuccess = { onSaved() },
                onFailure = { mutable.updateError(it.message ?: "Could not save the result") },
            )
        }
    }
    private fun MutableStateFlow<DispositionUiState>.updateError(message: String) { value = value.copy(saving = false, error = message) }
}
