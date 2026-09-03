package com.callflow.app.telecom

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.ui.theme.CallFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    @Inject lateinit var controller: CallUiController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { CallFlowTheme { InCallScreen(controller, onFinished = ::finish) } }
    }
}

@Composable
private fun InCallScreen(controller: CallUiController, onFinished: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.hasCall) { if (!state.hasCall) onFinished() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
                    Icon(Icons.Outlined.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(25.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(state.displayName ?: if (state.phoneNumber.isBlank()) "Unknown caller" else "Business call", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(state.phoneNumber, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(callStateLabel(state), modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                state.connectedAtMillis?.let { CallTimer(it) }
            }
            Spacer(Modifier.height(42.dp))
            if (state.incoming && state.state == PlatformCallState.RINGING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundCallButton(Color(0xFFB3261E), Icons.Outlined.CallEnd, "Decline", controller::reject)
                    RoundCallButton(Color(0xFF217A45), Icons.Outlined.Call, "Answer", controller::answer)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToggleControl(Icons.Outlined.MicOff, "Mute", state.muted, controller::toggleMute)
                        ToggleControl(Icons.AutoMirrored.Outlined.VolumeUp, "Speaker", state.speaker, controller::toggleSpeaker)
                        ToggleControl(Icons.Outlined.Pause, if (state.state == PlatformCallState.HOLDING) "Resume" else "Hold", state.state == PlatformCallState.HOLDING, controller::toggleHold, state.canHold)
                        ToggleControl(Icons.Outlined.Dialpad, "Keypad", state.keypadVisible, controller::toggleKeypad)
                    }
                    if (state.keypadVisible) DtmfKeypad(controller::sendDtmf)
                    RoundCallButton(Color(0xFFB3261E), Icons.Outlined.CallEnd, "End", controller::disconnect)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun RoundCallButton(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Button(onClick = onClick, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.size(72.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Icon(icon, label, Modifier.size(32.dp)) }; Text(label, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun ToggleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit, enabled: Boolean = true) = Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(54.dp).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Icon(icon, label, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f)) }; Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun DtmfKeypad(onDigit: (Char) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    listOf("123", "456", "789", "*0#").forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            row.forEach { digit ->
                IconButton(onClick = { onDigit(digit) }, modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                    Text(digit.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CallTimer(connectedAtMillis: Long) {
    val elapsed by produceState(0L, connectedAtMillis) {
        while (true) { value = ((System.currentTimeMillis() - connectedAtMillis) / 1000).coerceAtLeast(0); delay(1_000) }
    }
    val hours = elapsed / 3600
    val minutes = elapsed % 3600 / 60
    val seconds = elapsed % 60
    Text(if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds), modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

private fun callStateLabel(state: InCallUiState) = when (state.state) {
    PlatformCallState.RINGING -> "Incoming call"
    PlatformCallState.DIALING -> "Calling…"
    PlatformCallState.ACTIVE -> "Connected"
    PlatformCallState.HOLDING -> "On hold"
    PlatformCallState.DISCONNECTED -> "Call ended"
}
