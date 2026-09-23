package com.callflow.app.ui.auth

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WorkspaceConfigViewModel @Inject constructor(val endpoint: com.callflow.app.data.remote.CrmEndpoint) : ViewModel()

@Composable
fun WorkspaceSupport(allowConfiguration: Boolean = false, viewModel: WorkspaceConfigViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf(viewModel.endpoint.url) }
    var connector by remember { mutableStateOf(viewModel.endpoint.connector) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        TextButton(onClick = { page = "CRM connection" }) { Text("CRM connection") }
        TextButton(onClick = { page = "Privacy & terms" }) { Text("Privacy, terms & support") }
    }
    page?.let { title ->
        AlertDialog(onDismissRequest = { page = null }, title = { Text(title) }, text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (title == "CRM connection") {
                    Text("Connect any CRM through a CallFlow-compatible HTTPS adapter. Your administrator maps leads, calls, statuses and follow-ups on that adapter. CRM API keys stay on your server.")
                    Text("Configure before the first connection. The destination is then locked to prevent mixing accounts or sending records to another CRM.")
                    OutlinedTextField(url, { url = it }, label = { Text("Connector base URL") }, enabled = allowConfiguration && !viewModel.endpoint.locked, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(connector, { connector = it }, label = { Text("Connector ID") }, enabled = allowConfiguration && !viewModel.endpoint.locked, modifier = Modifier.fillMaxWidth())
                    if (allowConfiguration && !viewModel.endpoint.locked) Button(onClick = { runCatching { viewModel.endpoint.configure(url, connector) }.onSuccess { error = "Saved. Sign in with this CRM's account to verify authentication and sync." }.onFailure { error = it.message } }) { Text("SAVE CONNECTION") }
                    error?.let { Text(it) }
                } else {
                    Text("CallFlow Privacy & Terms · 5 September 2026", style = MaterialTheme.typography.titleSmall)
                    Text("CallFlow processes your account, assigned leads, phone numbers, call direction, status, timestamps, duration, SIM metadata, notes and follow-ups for business calling and reports. With phone/call-log permission it can import device calls, including numbers not assigned as leads, and sync them with your organization's configured CRM while the app is not open. This feature records call metadata, not conversation audio.")
                    Text("Location is captured for enabled work check-ins and related work actions when you grant permission. WhatsApp opens only when you choose a messaging action; its own terms apply. Your organization controls CRM access and server retention. Local data remains on this device until removed; uninstalling does not delete CRM records.")
                    Text("Use this app only for authorized work, respect do-not-call requests and applicable communication rules, and keep account credentials private. Reports depend on device permissions, network availability and the connected CRM. Changing CRM requires a supported migration to protect pending records.")
                    Text("For access, correction, retention details or account/data deletion, contact support and your organization administrator. Deletion requests require identity verification; legally required retention may apply. Sending a request does not itself delete records.")
                    Text("Support: Yogeshkukadiya92@gmail.com\nMobile: 9825344428")
                    listOf("Public privacy policy" to "callflow-privacy.html", "Terms of use" to "callflow-terms.html", "Account and data deletion" to "callflow-delete-account.html").forEach { (label, path) ->
                        TextButton(onClick = {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://dashboard.coachforlife.in/$path"))) }
                                .onFailure { android.widget.Toast.makeText(context, "Open dashboard.coachforlife.in/$path in your browser.", android.widget.Toast.LENGTH_LONG).show() }
                        }) { Text(label) }
                    }
                    TextButton(onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:Yogeshkukadiya92@gmail.com"))
                            .putExtra(android.content.Intent.EXTRA_SUBJECT, "CallFlow account / data deletion request")
                        runCatching { context.startActivity(intent) }.onFailure { android.widget.Toast.makeText(context, "Email Yogeshkukadiya92@gmail.com for support or deletion requests.", android.widget.Toast.LENGTH_LONG).show() }
                    }) { Text("REQUEST DATA DELETION / SUPPORT") }
                }
            }
        }, confirmButton = { TextButton(onClick = { page = null }) { Text("CLOSE") } })
    }
}
