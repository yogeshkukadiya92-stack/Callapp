package com.callflow.app.ui.leads

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.DatePickerDialog

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.FloatingActionButton
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
import com.callflow.app.core.model.LeadCallStats
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

private val leadDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@Composable
fun LeadsScreen(onLeadClick: (String) -> Unit, onCallLead: (String) -> Unit, viewModel: LeadsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filterMenuOpen by remember { mutableStateOf(false) }
    var advancedFiltersOpen by remember { mutableStateOf(false) }
    val filterOptions = buildList {
        add("ALL" to "All leads")
        add("NEW" to "New leads")
        add("OLD" to "Old leads")
        state.stageCounts.keys.sorted().filterNot { it.contains("new", ignoreCase = true) }.forEach { stage -> add(stage to stage.displayStage()) }
    }.distinctBy { it.first.uppercase() }
    val selectedLabel = filterOptions.firstOrNull { it.first.equals(state.selectedFilter, ignoreCase = true) }?.second ?: state.selectedFilter.displayStage()
    fun pickDate(current: LocalDate?, onSelected: (LocalDate) -> Unit) {
        val initial = current ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
    if (advancedFiltersOpen) {
        AlertDialog(
            onDismissRequest = { advancedFiltersOpen = false },
            title = { Text("Find leads") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Combine filters to narrow large lead lists.", color = Slate)
                    LeadFilterDropdown("Source", state.selectedSource, listOf("ALL") + state.sources, viewModel::setSource)
                    LeadFilterDropdown("Lead quality", state.selectedQuality, listOf("ALL") + state.qualities, viewModel::setQuality)
                    LeadFilterDropdown("Lead score", state.selectedScore, listOf("ALL", "0-25", "26-50", "51-75", "76-100"), viewModel::setScore)
                    LeadFilterDropdown("City", state.selectedCity, listOf("ALL") + state.cities, viewModel::setCity)
                    LeadFilterDropdown("Call status", state.selectedCallability, listOf("ALL", "CALLABLE", "DNC"), viewModel::setCallability)
                    LeadFilterDropdown("Duplicate status", state.selectedDuplicates, listOf("ALL", "UNIQUE", "DUPLICATE"), viewModel::setDuplicates)
                    LeadFilterDropdown("Contact activity", state.selectedContactStatus, listOf("ALL", "NEVER_CONTACTED", "CONTACTED", "CONNECTED", "NEVER_CONNECTED", "DUE", "OVERDUE"), viewModel::setContactStatus)
                    LeadFilterDropdown("Sort by", state.selectedSort, listOf("PRIORITY", "NEWEST", "OLDEST", "LAST_CONTACT", "SCORE_HIGH", "SCORE_LOW", "NAME"), viewModel::setSort)
                }
            },
            confirmButton = { Button(onClick = { advancedFiltersOpen = false }) { Text("SHOW ${state.leads.size} LEADS") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { viewModel.clearAllFilters(); advancedFiltersOpen = false }) { Text("RESET ALL") } },
        )
    }
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
            LeadCountCard("Uncalled", state.neverContacted, Modifier.weight(1f))
            LeadCountCard("Overdue", state.overdue, Modifier.weight(1f))
        }
        OutlinedButton(onClick = { advancedFiltersOpen = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Outlined.FilterList, null)
            Text(if (state.activeFilterCount == 0) "  MORE FILTERS" else "  FILTERS ACTIVE · ${state.activeFilterCount}")
        }
        OutlinedTextField(state.query, viewModel::setQuery, leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Search assigned leads") }, supportingText = { Text("Name, phone, company or city") }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDate(state.startDate, viewModel::setStartDate) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.DateRange, null)
                Text("  ${state.startDate?.format(leadDateFormatter) ?: "From date"}")
            }
            OutlinedButton(onClick = { pickDate(state.endDate, viewModel::setEndDate) }, modifier = Modifier.weight(1f)) {
                Text(state.endDate?.format(leadDateFormatter) ?: "To date")
            }
        }
        if (state.startDate != null || state.endDate != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${state.leads.size} leads in selected dates", color = Indigo, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.TextButton(onClick = viewModel::clearDateFilter) { Text("CLEAR DATE") }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.leads.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(if (state.query.isBlank() && state.selectedFilter == "ALL" && state.startDate == null && state.endDate == null) "No assigned leads" else "No matching leads", fontWeight = FontWeight.SemiBold); Text(if (state.query.isBlank() && state.selectedFilter == "ALL" && state.startDate == null && state.endDate == null) "New dashboard assignments appear automatically after sync." else "Try another search, status, or date filter.", color = Slate) } } }
            items(state.leads, key = Lead::id) { lead -> LeadRow(lead, state.contactStats[lead.id], { onLeadClick(lead.id) }, { onCallLead(lead.id) }) }
        }
    }
}

@Composable
private fun LeadCountCard(label: String, count: Int, modifier: Modifier = Modifier) = Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium, color = Slate) }
}

private fun String.displayStage() = replace('_', ' ').replace('-', ' ').lowercase().split(' ').filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable
private fun LeadRow(lead: Lead, stats: LeadCallStats?, onClick: () -> Unit, onCall: () -> Unit) = PremiumCard(
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
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(lead.name, fontWeight = FontWeight.SemiBold)
            Text(lead.shortDescription(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary)
            Text(buildList { add(lead.stageId.replace('_', ' ').uppercase()); lead.quality?.takeIf(String::isNotBlank)?.let { add(it.uppercase()) } }.joinToString(" · "), color = Indigo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(stats?.lastContactedAt?.let { "Last call ${DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(it)} · ${stats.attempts} attempts" } ?: "Never contacted", color = if (stats?.lastContactedAt == null) MaterialTheme.colorScheme.error else Slate, style = MaterialTheme.typography.labelMedium)
            if (lead.doNotCall) Text("DO NOT CALL", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) else if (lead.duplicateCount > 1) Text("POSSIBLE DUPLICATE · ${lead.duplicateCount}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = lead.scoreBadgeColor()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("SCORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(lead.score.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            FloatingActionButton(
                onClick = { if (!lead.doNotCall) onCall() },
                modifier = Modifier.semantics { contentDescription = if (lead.doNotCall) "Call blocked for ${lead.name}" else "Call ${lead.name}" },
                containerColor = if (lead.doNotCall) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Outlined.Call, null, tint = if (lead.doNotCall) Slate else Indigo) }
        }
    }
}

@Composable
private fun Lead.scoreBadgeColor() = when {
    score >= 76 -> com.callflow.app.ui.theme.Emerald.copy(alpha = .18f)
    score >= 51 -> MaterialTheme.colorScheme.primaryContainer
    score >= 26 -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.errorContainer
}

internal fun Lead.shortDescription(): String {
    val details = listOfNotNull(company?.takeIf(String::isNotBlank), city?.takeIf(String::isNotBlank), campaignId?.takeIf(String::isNotBlank))
    val context = details.ifEmpty { listOf("Assigned sales lead") }.joinToString(" · ")
    val date = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault()).format(updatedAt)
    return "$context · Updated $date"
}

@Composable
private fun LeadFilterDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${selected.filterLabel()}", modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.FilterList, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.distinct().forEach { value -> DropdownMenuItem(text = { Text(value.filterLabel(), fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal) }, onClick = { onSelected(value); open = false }) }
        }
    }
}

private fun String.filterLabel() = when (this) {
    "ALL" -> "All"
    "DNC" -> "Do Not Call"
    "SCORE_HIGH" -> "Score: high to low"
    "SCORE_LOW" -> "Score: low to high"
    "NAME" -> "Name: A to Z"
    else -> replace('_', ' ').lowercase().split(' ').filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
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
        item { SectionHeader("Call performance", if (state.stats.attempts == 0) "Never contacted" else "${state.stats.connected} connected") }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LeadStatCard("Attempts", state.stats.attempts.toString(), Modifier.weight(1f)); LeadStatCard("Connected", state.stats.connected.toString(), Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LeadStatCard("Talk time", leadDuration(state.stats.talkTimeSeconds), Modifier.weight(1f)); LeadStatCard("Not connected", state.stats.notConnected.toString(), Modifier.weight(1f)) }
        } }
        if (state.stats.attempts > 0) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Contact history", fontWeight = FontWeight.Bold); Text("First call: ${state.stats.firstContactedAt?.let(::leadContactTime) ?: "—"}", color = Slate); Text("Last call: ${state.stats.lastContactedAt?.let(::leadContactTime) ?: "—"}", color = Slate); if (state.stats.missed > 0) Text("${state.stats.missed} missed incoming call${if (state.stats.missed == 1) "" else "s"}", color = MaterialTheme.colorScheme.error) } } }
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

@Composable private fun LeadStatCard(label: String, value: String, modifier: Modifier = Modifier) = Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, color = Slate, style = MaterialTheme.typography.labelMedium) } }
private fun leadDuration(seconds: Long): String { val safe = seconds.coerceAtLeast(0); val hours = safe / 3600; val minutes = safe % 3600 / 60; val remainder = safe % 60; return if (hours > 0) "${hours}h ${minutes}m" else if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s" }
private fun leadContactTime(value: java.time.Instant) = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault()).format(value)
