package com.callflow.app.ui.leads

import android.content.Intent
import android.net.Uri
import android.widget.Toast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LeadsScreen(onLeadClick: (String) -> Unit, viewModel: LeadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filterMenuOpen by remember { mutableStateOf(false) }
    val filterOptions = buildList {
        add("ALL" to "All leads")
        add("NEW" to "New leads")
        add("OLD" to "Old leads")
        state.stageCounts.keys.sorted().filterNot { it.contains("new", ignoreCase = true) }.forEach { stage -> add(stage to stage.displayStage()) }
    }.distinctBy { it.first.uppercase() }
    val selectedLabel = filterOptions.firstOrNull { it.first.equals(state.selectedFilter, ignoreCase = true) }?.second ?: state.selectedFilter.displayStage()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column { Text("Leads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Text("${state.totalLeads} assigned leads", color = Slate) }
            Box {
                OutlinedButton(onClick = { filterMenuOpen = true }) { Icon(Icons.Outlined.FilterList, null); Text("  $selectedLabel") }
                DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                    filterOptions.forEach { (value, label) ->
                        val count = when (value) { "ALL" -> state.totalLeads; "NEW" -> state.newLeads; "OLD" -> state.totalLeads - state.newLeads; else -> state.stageCounts[value] ?: 0 }
                        DropdownMenuItem(text = { Text("$label  ·  $count", fontWeight = if (state.selectedFilter.equals(value, true)) FontWeight.Bold else FontWeight.Normal) }, onClick = { viewModel.setFilter(value); filterMenuOpen = false })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeadCountCard("Total", state.totalLeads, Modifier.weight(1f))
            LeadCountCard("New", state.newLeads, Modifier.weight(1f))
            LeadCountCard("Old", state.totalLeads - state.newLeads, Modifier.weight(1f))
        }
        OutlinedTextField(state.query, viewModel::setQuery, leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Search assigned leads") }, supportingText = { Text("Name, phone, company or city") }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.leads.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(if (state.query.isBlank() && state.selectedFilter == "ALL") "No assigned leads" else "No matching leads", fontWeight = FontWeight.SemiBold); Text(if (state.query.isBlank() && state.selectedFilter == "ALL") "New dashboard assignments appear automatically after sync." else "Try another search or lead filter.", color = Slate) } } }
            items(state.leads, key = Lead::id) { lead -> LeadRow(lead, { onLeadClick(lead.id) }) }
        }
    }
}

@Composable
private fun LeadCountCard(label: String, count: Int, modifier: Modifier = Modifier) = Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = Slate) }
}

private fun String.displayStage() = replace('_', ' ').replace('-', ' ').lowercase().split(' ').filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable
private fun LeadRow(lead: Lead, onClick: () -> Unit) = PremiumCard(
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
        Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.SemiBold); Text(listOfNotNull(lead.company, lead.city).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary); if (lead.doNotCall) Text("DO NOT CALL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) else if (lead.duplicateCount > 1) Text("POSSIBLE DUPLICATE · ${lead.duplicateCount}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium) }
        Text(lead.stageId.replace('_', ' ').uppercase(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun LeadDetailScreen(onBack: () -> Unit, onCall: (String) -> Unit, viewModel: LeadDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.loading) return Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Loading lead…", color = Slate, modifier = Modifier.padding(top = 12.dp)) }
    val lead = state.lead ?: return Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text("Lead unavailable", style = MaterialTheme.typography.titleLarge); Text("This lead may have been reassigned or removed during sync.", color = Slate, modifier = Modifier.padding(top = 8.dp)); androidx.compose.material3.OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 18.dp)) { Text("BACK TO LEADS") } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }; Text("Lead details", style = MaterialTheme.typography.titleLarge) } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(lead.name, style = MaterialTheme.typography.headlineMedium); lead.company?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Slate) }; Text(lead.displayPhone, color = Indigo); Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)) { Text(lead.stageId.replace('_', ' ').uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) } } } }
        if (lead.doNotCall) item { Card { Column(Modifier.padding(16.dp)) { Text("Do Not Call", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); Text("This number is blocked on the dashboard. Remove DNC there before calling.") } } }
        else if (lead.duplicateCount > 1) item { Card { Column(Modifier.padding(16.dp)) { Text("Possible duplicate", fontWeight = FontWeight.Bold); Text("${lead.duplicateCount} records use this phone number. You will be asked to confirm on the calling screen.") } } }
        item { Button(onClick = { onCall(lead.id) }, enabled = !lead.doNotCall, modifier = Modifier.fillMaxWidth().height(58.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Call, null); Text(if (lead.doNotCall) "  CALL BLOCKED" else "  CALL NOW") } }
        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { androidx.compose.material3.OutlinedButton(onClick = {
            val config = state.engagement
            if (config == null) { Toast.makeText(context, "WhatsApp template is still loading", Toast.LENGTH_SHORT).show(); return@OutlinedButton }
            val message = config.whatsappTemplate.replace("{{leadName}}", lead.name).replace("{{company}}", lead.company.orEmpty()).replace("{{salespersonName}}", config.salespersonName)
            val phone = lead.normalizedPhone.filter(Char::isDigit)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}"))) }.onFailure { Toast.makeText(context, "WhatsApp could not be opened", Toast.LENGTH_SHORT).show() }
        }, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = state.engagement != null) { Icon(Icons.AutoMirrored.Outlined.Send, null); Text(if (state.engagementLoading) "  LOADING TEMPLATE…" else "  SEND WHATSAPP") }
            if (!state.engagementLoading && state.engagement == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("WhatsApp template is unavailable.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); androidx.compose.material3.TextButton(onClick = viewModel::refreshEngagement) { Text("RETRY") } }
        } }
        item { SectionHeader("Activity timeline") }
        if (state.timeline.isEmpty()) item { Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.timeline, key = { it.id }) { event -> PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(event.title, fontWeight = FontWeight.SemiBold); Text(DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(event.occurredAt), style = MaterialTheme.typography.labelMedium, color = Slate); event.detail?.let { Text(it) } } } }
        item { Spacer(Modifier.height(64.dp)) }
    }
}
