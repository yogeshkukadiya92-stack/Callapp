package com.callflow.app.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.BuildConfig
import com.callflow.app.core.model.FollowUpStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.callflow.app.ui.theme.BarChart
import com.callflow.app.ui.theme.ActivityChart
import com.callflow.app.ui.theme.DonutChart
import com.callflow.app.ui.theme.Emerald
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate

private val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

@Composable fun CallsScreen(viewModel: CallsViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("7 days") }
    val visibleCalls = calls.filter { call -> (filter == "All" || (filter == "Connected" && call.answeredAt != null) || (filter == "Missed" && call.answeredAt == null)) && call.phone.contains(query, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Call analysis", style = MaterialTheme.typography.headlineMedium); Text("Your performance at a glance", color = Slate) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Today", "7 days", "30 days").forEach { value -> FilterChip(range == value, { range = value }, label = { Text(value) }) } } }
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    DonutChart(analysis.connectionRatePercent, Modifier.width(118.dp).height(118.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnalysisMetric("Total calls", analysis.totalCalls.toString())
                        AnalysisMetric("Average talk", formatDuration(analysis.averageTalkTimeSeconds))
                        AnalysisMetric("Total talk", formatDuration(analysis.totalTalkTimeSeconds))
                    }
                }
            }
        }
        item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Peak calling hours", "Today")
                BarChart(listOf(8f, 15f, 23f, 18f, 28f, 20f), Modifier.fillMaxWidth().height(105.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("9a", "11a", "1p", "3p", "5p", "7p").forEach { Text(it, color = Slate, style = MaterialTheme.typography.labelSmall) } }
            } }
        }
        item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Conversion trend", "+12.4%")
                ActivityChart(listOf(11f, 14f, 13f, 18f, 17f, 22f, 25f))
            } }
        }
        item { SectionHeader("Call history") }
        item { OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search calls") }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Connected", "Missed").forEach { value -> FilterChip(filter == value, { filter = value }, label = { Text(value) }) } } }
        if (visibleCalls.isEmpty()) item { Text("No calls found", color = Slate) }
        items(visibleCalls, key = { it.id }) { call ->
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(15.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (call.answeredAt != null) Emerald.copy(alpha = .12f) else MaterialTheme.colorScheme.error.copy(alpha = .10f), modifier = Modifier.width(42.dp).height(42.dp)) { Icon(Icons.Outlined.Call, null, tint = if (call.answeredAt != null) Emerald else MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(call.phone, fontWeight = FontWeight.SemiBold); Text("${formatter.format(call.startedAt)} · ${call.direction.name.lowercase().replaceFirstChar(Char::uppercase)}", color = Slate, style = MaterialTheme.typography.bodySmall) }
                    Text(if (call.answeredAt != null) formatDuration(call.answeredAt.let { start -> call.endedAt?.epochSecond?.minus(start.epochSecond) } ?: 0) else "Missed", color = if (call.answeredAt != null) Emerald else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable private fun AnalysisMetric(label: String, value: String) = Column {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium, color = Slate)
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s"
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
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Sync status", fontWeight = FontWeight.SemiBold); Text(if (pending == 0) "Up to date" else "$pending changes waiting"); if (conflicts > 0) Text("$conflicts conflicts require support review", color = MaterialTheme.colorScheme.error); Text(if (BuildConfig.USE_FAKE_BACKEND) "Environment: Local fake backend" else "Environment: ${BuildConfig.DASHBOARD_CONNECTOR_ID}"); Text("API: ${BuildConfig.API_BASE_URL}"); Button(onClick = viewModel::retry) { Text("Retry sync") } } }
        Text("Call tracking permissions are requested only when you enable the Phone role. Manual CRM mode remains available.")
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Permissions", fontWeight = FontWeight.SemiBold); Text("Phone role: ${viewModel.permissions.callTracking.name.replace('_', ' ').lowercase()}"); Text("Call notifications: ${viewModel.permissions.notifications.name.replace('_', ' ').lowercase()}"); Text("Place calls: ${viewModel.permissions.calling.name.replace('_', ' ').lowercase()}") } }
        Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("SIGN OUT") }
    }
}
