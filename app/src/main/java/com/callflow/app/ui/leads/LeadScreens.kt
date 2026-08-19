package com.callflow.app.ui.leads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.core.model.Lead
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LeadsScreen(onLeadClick: (String) -> Unit, viewModel: LeadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Leads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        OutlinedTextField(state.query, viewModel::setQuery, label = { Text("Search name, phone, company or city") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.leads, key = Lead::id) { lead -> LeadRow(lead, { onLeadClick(lead.id) }) }
        }
    }
}

@Composable
private fun LeadRow(lead: Lead, onClick: () -> Unit) = Card(
    Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = buildList {
                add(lead.name)
                lead.company?.let(::add)
                lead.city?.let(::add)
                add(lead.displayPhone)
                add("Stage ${lead.stageId.replace('_', ' ')}")
            }.joinToString(", ")
        }
        .clickable(onClick = onClick),
) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.SemiBold); Text(listOfNotNull(lead.company, lead.city).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary) }
        Text(lead.stageId.replace('_', ' ').uppercase(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun LeadDetailScreen(onCall: (String) -> Unit, viewModel: LeadDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lead = state.lead ?: return Column(Modifier.padding(24.dp)) { Text("Lead not found") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(lead.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); lead.company?.let { Text(it, style = MaterialTheme.typography.titleMedium) }; Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary); Text(lead.stageId.uppercase(), style = MaterialTheme.typography.labelLarge) }
        item { Button(onClick = { onCall(lead.id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Outlined.Call, null); Text("  CALL NOW") } }
        item { Text("Timeline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (state.timeline.isEmpty()) item { Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.timeline, key = { it.id }) { event -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(event.title, fontWeight = FontWeight.SemiBold); Text(DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(event.occurredAt), style = MaterialTheme.typography.labelMedium); event.detail?.let { Text(it) } } } }
        item { Spacer(Modifier.height(64.dp)) }
    }
}
