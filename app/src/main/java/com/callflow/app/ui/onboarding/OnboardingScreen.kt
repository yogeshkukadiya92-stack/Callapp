package com.callflow.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OnboardingPage(val title: String, val body: String, val detail: String, val icon: ImageVector)
private val pages = listOf(
    OnboardingPage("Welcome to CallFlow", "Manage business calls and follow-ups with less typing.", "Your downloaded leads remain available during poor connectivity, and work is saved locally before synchronization.", Icons.Outlined.Call),
    OnboardingPage("Your business data", "CallFlow stores only information needed for sales activity.", "Customer numbers, notes, follow-ups, and call outcomes are protected as business data. Passwords are never stored and session tokens are encrypted.", Icons.Outlined.Security),
    OnboardingPage("Optional call tracking", "Automatic tracking requires the Android Phone role.", "CallFlow explains and requests the role only when you enable call tracking. If you decline, calling, notes, dispositions, and follow-ups remain available in manual mode.", Icons.Outlined.PhoneAndroid),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val value = pages[page]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp), verticalArrangement = Arrangement.Center) {
        Icon(value.icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(value.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text(value.body, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
        Text(value.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(40.dp))
        Text("${page + 1} of ${pages.size}", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text("BACK") }
            Button(onClick = { if (page == pages.lastIndex) onComplete() else page++ }, modifier = Modifier.weight(1f)) { Text(if (page == pages.lastIndex) "CONTINUE TO SIGN IN" else "NEXT") }
        }
    }
}
