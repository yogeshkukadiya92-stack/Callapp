package com.callflow.app.telecom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallProximityPolicyTest {
    @Test
    fun `sensor protects dialing and connected calls on earpiece`() {
        assertTrue(shouldUseProximitySensor(callState(PlatformCallState.DIALING)))
        assertTrue(shouldUseProximitySensor(callState(PlatformCallState.ACTIVE)))
        assertTrue(shouldUseProximitySensor(callState(PlatformCallState.HOLDING)))
    }

    @Test
    fun `sensor stays off while ringing or after call ends`() {
        assertFalse(shouldUseProximitySensor(callState(PlatformCallState.RINGING)))
        assertFalse(shouldUseProximitySensor(InCallUiState()))
    }

    @Test
    fun `speaker and keypad keep screen available`() {
        assertFalse(shouldUseProximitySensor(callState(PlatformCallState.ACTIVE).copy(speaker = true)))
        assertFalse(shouldUseProximitySensor(callState(PlatformCallState.ACTIVE).copy(keypadVisible = true)))
    }

    private fun callState(state: PlatformCallState) = InCallUiState(
        hasCall = true,
        state = state,
    )
}
