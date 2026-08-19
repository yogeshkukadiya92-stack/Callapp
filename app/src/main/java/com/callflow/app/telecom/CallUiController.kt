package com.callflow.app.telecom

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PlatformCallState { RINGING, DIALING, ACTIVE, HOLDING, DISCONNECTED }

data class InCallUiState(
    val hasCall: Boolean = false,
    val phoneNumber: String = "",
    val displayName: String? = null,
    val incoming: Boolean = false,
    val state: PlatformCallState = PlatformCallState.DISCONNECTED,
    val muted: Boolean = false,
    val speaker: Boolean = false,
)

@Singleton
class CallUiController @Inject constructor() {
    private val mutableState = MutableStateFlow(InCallUiState())
    val state: StateFlow<InCallUiState> = mutableState.asStateFlow()
    private var call: Call? = null
    private var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish(call, state)
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish(call, currentState(call))
    }

    fun attachService(value: InCallService) { service = value }
    fun detachService(value: InCallService) { if (service === value) service = null }

    fun attachCall(value: Call) {
        call?.unregisterCallback(callback)
        call = value
        value.registerCallback(callback)
        publish(value, currentState(value))
    }

    fun detachCall(value: Call) {
        value.unregisterCallback(callback)
        if (call === value) { call = null; mutableState.value = InCallUiState() }
    }

    fun answer() { call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun reject() { call?.reject(false, null) }
    fun disconnect() { call?.disconnect() }
    fun toggleHold() { call?.let { if (currentState(it) == Call.STATE_HOLDING) it.unhold() else it.hold() } }
    fun toggleMute() {
        val next = !mutableState.value.muted
        service?.setMuted(next)
        mutableState.value = mutableState.value.copy(muted = next)
    }
    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val next = !mutableState.value.speaker
        service?.setAudioRoute(if (next) android.telecom.CallAudioState.ROUTE_SPEAKER else android.telecom.CallAudioState.ROUTE_EARPIECE)
        mutableState.value = mutableState.value.copy(speaker = next)
    }

    private fun publish(value: Call, platformState: Int) {
        val details = value.details
        val incoming = android.os.Build.VERSION.SDK_INT >= 29 && details.callDirection == Call.Details.DIRECTION_INCOMING
        val state = when (platformState) {
            Call.STATE_RINGING -> PlatformCallState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> PlatformCallState.DIALING
            Call.STATE_ACTIVE -> PlatformCallState.ACTIVE
            Call.STATE_HOLDING -> PlatformCallState.HOLDING
            else -> PlatformCallState.DISCONNECTED
        }
        mutableState.value = mutableState.value.copy(hasCall = state != PlatformCallState.DISCONNECTED, phoneNumber = details.handle?.schemeSpecificPart.orEmpty(), displayName = details.callerDisplayName?.toString(), incoming = incoming, state = state)
    }

    @Suppress("DEPRECATION")
    private fun currentState(value: Call): Int = if (android.os.Build.VERSION.SDK_INT >= 31) value.details.state else value.state
}
