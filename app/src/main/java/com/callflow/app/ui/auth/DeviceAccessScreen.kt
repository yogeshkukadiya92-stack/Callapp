package com.callflow.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callflow.app.core.model.DeviceStatus

@Composable
fun DeviceAccessScreen(status: DeviceStatus, checking: Boolean, error: String?, onCheckAgain: () -> Unit, onLogout: () -> Unit) {
    val title = when (status) {
        DeviceStatus.PENDING_APPROVAL -> "New device approval required"
        DeviceStatus.BLOCKED -> "This device is blocked"
        DeviceStatus.REVOKED -> "Device access was revoked"
        DeviceStatus.ACTIVE -> "Device approved"
    }
    val body = when (status) {
        DeviceStatus.PENDING_APPROVAL -> "Ask your manager to approve this device. CallFlow will remain locked until approval is confirmed."
        DeviceStatus.BLOCKED -> "Your organization has blocked this device. Contact your administrator for assistance."
        DeviceStatus.REVOKED -> "This session can no longer access business data. Sign out or contact your administrator."
        DeviceStatus.ACTIVE -> "You can continue to CallFlow."
    }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp)) }
        if (status == DeviceStatus.PENDING_APPROVAL) Button(onClick = onCheckAgain, enabled = !checking, modifier = Modifier.fillMaxWidth()) { if (checking) CircularProgressIndicator() else Text("CHECK AGAIN") }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("SIGN OUT") }
    }
}
