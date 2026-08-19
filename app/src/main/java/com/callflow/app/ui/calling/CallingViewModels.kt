package com.callflow.app.ui.calling

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callflow.app.core.model.DispositionInput
import com.callflow.app.core.model.DispositionOption
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.Outcome
import com.callflow.app.data.repository.OfflineCallRepository
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.telecom.CallIntegrationManager
import com.callflow.app.telecom.CallIntegrationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class CallingUiState(val lead: Lead? = null, val integrationState: CallIntegrationState = CallIntegrationState.ManualMode, val callId: String? = null, val error: String? = null)

@HiltViewModel
class CallingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val leads: LeadRepository,
    private val calls: CallRepository,
    private val integration: CallIntegrationManager,
) : ViewModel() {
    private val leadId: String = checkNotNull(savedStateHandle["leadId"])
    private val mutable = MutableStateFlow(CallingUiState(integrationState = integration.state()))
    val state: StateFlow<CallingUiState> = combine(leads.observeLead(leadId), mutable) { lead, local -> local.copy(lead = lead) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mutable.value)
    fun roleIntent() = integration.roleRequestIntent()
    fun refreshRole() { mutable.value = mutable.value.copy(integrationState = integration.state()) }
    fun call(onStarted: (String) -> Unit) {
        val lead = state.value.lead ?: return
        viewModelScope.launch {
            calls.startOutgoingCall(lead).fold(onSuccess = { id ->
                when (integration.initiateCall(lead.displayPhone)) {
                    is Outcome.Success -> { mutable.value = mutable.value.copy(callId = id); onStarted(id) }
                    is Outcome.Failure -> mutable.value = mutable.value.copy(error = "Could not open the phone app. The call attempt remains saved.")
                }
            }, onFailure = { mutable.value = mutable.value.copy(error = "Could not save this call attempt") })
        }
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
    fun note(value: String) { mutable.value = mutable.value.copy(note = value) }
    fun schedule(secondsFromNow: Long) { mutable.value = mutable.value.copy(followUpAt = Instant.now().plusSeconds(secondsFromNow)) }
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
