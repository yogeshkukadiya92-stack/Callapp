package com.callflow.app.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.BuildConfig
import com.callflow.app.core.model.FollowUpStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

@Composable fun CallsScreen(viewModel: CallsViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Calls", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (calls.isEmpty()) item { Text("No calls logged yet") }
        items(calls, key = { it.id }) { call -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(call.phone, fontWeight = FontWeight.SemiBold); Text("${call.direction.name.lowercase().replaceFirstChar(Char::uppercase)} · ${formatter.format(call.startedAt)}"); Text(if (call.answeredAt != null) "Connected" else "Attempted", color = MaterialTheme.colorScheme.primary) } } }
    }
}

@Composable fun FollowUpsScreen(viewModel: FollowUpsViewModel = hiltViewModel()) {
    val values by viewModel.followUps.collectAsStateWithLifecycle()
    val pending = values.filter { it.status == FollowUpStatus.PENDING }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Follow-ups", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (pending.isEmpty()) item { Text("No pending follow-ups") }
        items(pending, key = { it.id }) { value -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(formatter.format(value.scheduledAt), fontWeight = FontWeight.SemiBold); value.note?.let { Text(it) } }; Button(onClick = { viewModel.complete(value.id) }) { Text("Done") } } } }
    }
}

@Composable fun MoreScreen(viewModel: SyncStatusViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Sync status", fontWeight = FontWeight.SemiBold); Text(if (pending == 0) "Up to date" else "$pending changes waiting"); if (conflicts > 0) Text("$conflicts conflicts require support review", color = MaterialTheme.colorScheme.error); Text(if (BuildConfig.USE_FAKE_BACKEND) "Environment: Local fake backend" else "Environment: Production API"); Button(onClick = viewModel::retry) { Text("Retry sync") } } }
        Text("Call tracking permissions are requested only when you enable the Phone role. Manual CRM mode remains available.")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Permissions", fontWeight = FontWeight.SemiBold); Text("Phone role: ${viewModel.permissions.callTracking.name.replace('_', ' ').lowercase()}"); Text("Call notifications: ${viewModel.permissions.notifications.name.replace('_', ' ').lowercase()}"); Text("Place calls: ${viewModel.permissions.calling.name.replace('_', ' ').lowercase()}") } }
        Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("SIGN OUT") }
    }
}
