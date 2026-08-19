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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.ui.theme.CallFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        setContent { CallFlowTheme { InCallScreen(controller, onFinished = ::finishAndRemoveTask) } }
    }
}

@Composable
private fun InCallScreen(controller: CallUiController, onFinished: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.hasCall) { if (!state.hasCall) onFinished() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.size(32.dp))
                Text(state.displayName ?: "Business call", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(state.phoneNumber, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.state.name.lowercase().replaceFirstChar(Char::uppercase), modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary)
            }
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
                        ToggleControl(Icons.Outlined.Pause, if (state.state == PlatformCallState.HOLDING) "Resume" else "Hold", state.state == PlatformCallState.HOLDING, controller::toggleHold)
                    }
                    RoundCallButton(Color(0xFFB3261E), Icons.Outlined.CallEnd, "End", controller::disconnect)
                }
            }
        }
    }
}

@Composable private fun RoundCallButton(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Button(onClick = onClick, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.size(72.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Icon(icon, label, Modifier.size(32.dp)) }; Text(label, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun ToggleControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, modifier = Modifier.size(56.dp).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { Icon(icon, label) }; Text(label, style = MaterialTheme.typography.labelLarge) }
