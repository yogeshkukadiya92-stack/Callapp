package com.callflow.app.ui.operations

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.BuildConfig
import com.callflow.app.core.model.FollowUpStatus
import com.callflow.app.core.model.PermissionState
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.CallStatus
import com.callflow.app.core.model.status
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.callflow.app.ui.theme.BarChart
import com.callflow.app.ui.theme.ActivityChart
import com.callflow.app.ui.theme.DonutChart
import com.callflow.app.ui.theme.Emerald
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import androidx.core.content.ContextCompat
import com.callflow.app.core.call.CallAnalysisCalculator
import com.callflow.app.reports.ReportExporter

private val formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

@Composable fun CallsScreen(initialFilter: String = "All", viewModel: CallsViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("7 days") }
    val rangeStart = remember(range) {
        val days = when (range) { "Today" -> 1L; "30 days" -> 30L; else -> 7L }
        LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
    }
    val visibleCalls = calls.filter { call -> call.startedAt >= rangeStart && (filter == "All" || filter == "Connected" && call.status == CallStatus.CONNECTED || filter == "Missed" && call.status == CallStatus.MISSED || filter == "Not connected" && call.status == CallStatus.NOT_CONNECTED) && call.phone.contains(query, ignoreCase = true) }
    val analysis = remember(visibleCalls) { CallAnalysisCalculator.calculate(visibleCalls) }
    val hourlyCalls = remember(visibleCalls) { (9..20 step 2).map { hour -> visibleCalls.count { it.startedAt.atZone(ZoneId.systemDefault()).hour in hour until hour + 2 }.toFloat() } }
    val dailyCalls = remember(visibleCalls, range) {
        val days = when (range) { "Today" -> 1; "30 days" -> 30; else -> 7 }
        (days - 1 downTo 0).map { offset -> val date = LocalDate.now().minusDays(offset.toLong()); visibleCalls.count { it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() == date }.toFloat() }
    }
    val dailyLabels = remember(range) {
        when (range) {
            "Today" -> listOf("Today")
            "30 days" -> listOf("30 days ago", "Today")
            else -> (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).dayOfWeek.name.take(1) }
        }
    }
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
        if (visibleCalls.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Peak calling hours", range)
                BarChart(hourlyCalls, Modifier.fillMaxWidth().height(105.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("9a", "11a", "1p", "3p", "5p", "7p").forEach { Text(it, color = Slate, style = MaterialTheme.typography.labelSmall) } }
            } }
        }
        if (visibleCalls.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Call trend", range)
                ActivityChart(dailyCalls, labels = dailyLabels)
            } }
        }
        item { SectionHeader("Call history", "${visibleCalls.size} calls") }
        item { OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search calls") }, singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("All", "Connected", "Missed", "Not connected")) { value -> FilterChip(filter == value, { filter = value }, label = { Text(value) }) } } }
        if (visibleCalls.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("No calls in this view", fontWeight = FontWeight.SemiBold); Text("Calls with assigned leads will appear after call-log permission is enabled and sync completes.", color = Slate) } } }
        items(visibleCalls, key = { it.id }) { call ->
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(15.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (call.answeredAt != null) Emerald.copy(alpha = .12f) else MaterialTheme.colorScheme.error.copy(alpha = .10f), modifier = Modifier.width(42.dp).height(42.dp)) { Icon(Icons.Outlined.Call, null, tint = if (call.answeredAt != null) Emerald else MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp)) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(call.phone, fontWeight = FontWeight.SemiBold); Text("${formatter.format(call.startedAt)} · ${call.direction.name.lowercase().replaceFirstChar(Char::uppercase)}", color = Slate, style = MaterialTheme.typography.bodySmall) }
                    val statusLabel = when (call.status) { CallStatus.CONNECTED -> formatDuration(call.answeredAt?.let { start -> call.endedAt?.epochSecond?.minus(start.epochSecond) } ?: 0); CallStatus.MISSED -> "Missed"; CallStatus.NOT_CONNECTED -> "Not connected" }
                    Text(statusLabel, color = if (call.status == CallStatus.CONNECTED) Emerald else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable fun ReportsScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shiftSummary by viewModel.shiftSummary.collectAsStateWithLifecycle()
    val shiftLoading by viewModel.shiftLoading.collectAsStateWithLifecycle()
    val shiftError by viewModel.shiftError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reportType by remember { mutableStateOf("Overview") }
    var range by remember { mutableStateOf("7 days") }
    var status by remember(reportType) { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    val start = remember(range) { when (range) { "Today" -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(); "7 days" -> LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant(); "30 days" -> LocalDate.now().minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant(); else -> Instant.EPOCH } }
    val calls = state.calls.filter { it.startedAt >= start && (status == "All" || status == "Connected" && it.status == CallStatus.CONNECTED || status == "Missed" && it.status == CallStatus.MISSED || status == "Not connected" && it.status == CallStatus.NOT_CONNECTED) && it.phone.contains(query, true) }
    val leads = state.leads.filter { it.updatedAt >= start && (status == "All" || status == "New" && it.stageId.contains("new", true) || status == "Old" && !it.stageId.contains("new", true) || it.stageId.equals(status, true)) && listOf(it.name, it.displayPhone, it.company.orEmpty(), it.city.orEmpty()).any { value -> value.contains(query, true) } }
    val followUps = state.followUps.filter { it.scheduledAt >= start && (status == "All" || it.status.name.equals(status, true)) && (it.note.orEmpty().contains(query, true) || it.leadId.contains(query, true)) }
    val connected = state.calls.count { it.startedAt >= start && it.answeredAt != null }
    val pendingFollowUps = state.followUps.count { it.scheduledAt >= start && it.status == FollowUpStatus.PENDING }
    val stageOptions = state.leads.map { it.stageId }.distinct().sorted()
    val statuses = when (reportType) { "Calls" -> listOf("All", "Connected", "Missed", "Not connected"); "Leads" -> (listOf("All", "New", "Old") + stageOptions).distinct(); "Follow-ups" -> listOf("All", "Pending", "Completed", "Cancelled", "Rescheduled", "Missed"); else -> emptyList() }
    val totalInRange = state.calls.count { it.startedAt >= start }
    val reportTitle = "CallFlow $reportType · $range${if (status == "All" || reportType == "Overview") "" else " · $status"}"
    val export = remember(reportType, range, status, query, calls, leads, followUps, connected, pendingFollowUps, shiftSummary) {
        when (reportType) {
            "Calls" -> ExportData(listOf("Phone", "Date", "Direction", "Status", "Duration"), calls.map { call -> listOf(call.phone, formatter.format(call.startedAt), call.direction.name.reportLabel(), call.status.name.reportLabel(), if (call.answeredAt == null) "0s" else formatDuration(call.endedAt?.epochSecond?.minus(call.answeredAt.epochSecond) ?: 0)) })
            "Leads" -> ExportData(listOf("Name", "Phone", "Company", "City", "Stage", "Updated"), leads.map { lead -> listOf(lead.name, lead.displayPhone, lead.company.orEmpty(), lead.city.orEmpty(), lead.stageId.reportLabel(), formatter.format(lead.updatedAt)) })
            "Follow-ups" -> ExportData(listOf("Lead ID", "Scheduled", "Status", "Priority", "Note"), followUps.map { value -> listOf(value.leadId, formatter.format(value.scheduledAt), value.status.name.reportLabel(), value.priority.toString(), value.note.orEmpty()) })
            "Shift" -> ExportData(listOf("Date", "Shift start", "Shift end", "Active time", "Calls", "Connected", "First call", "Last call", "Calls/hour"), shiftSummary?.last7Days.orEmpty().map { day -> listOf(day.date, serverTime(day.shiftStartedAt), serverTime(day.shiftEndedAt), formatDuration(day.activeSeconds), day.calls.toString(), day.connected.toString(), serverTime(day.firstCallAt), serverTime(day.lastCallAt), day.callsPerActiveHour.toString()) })
            else -> ExportData(listOf("Metric", "Value"), listOf(listOf("Total calls", totalInRange.toString()), listOf("Connected", connected.toString()), listOf("Connection rate", if (totalInRange == 0) "0%" else "${connected * 100 / totalInRange}%"), listOf("Leads", state.leads.count { it.updatedAt >= start }.toString()), listOf("Pending follow-ups", pendingFollowUps.toString())) + state.leads.filter { it.updatedAt >= start }.groupingBy { it.stageId }.eachCount().map { listOf("Stage: ${it.key.reportLabel()}", it.value.toString()) })
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Reports", onBack); Text("All synced sales activity in one place", color = Slate) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Overview", "Calls", "Leads", "Follow-ups", "Shift")) { value -> FilterChip(reportType == value, { reportType = value }, label = { Text(value) }) } } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Today", "7 days", "30 days", "All time")) { value -> FilterChip(range == value, { range = value }, label = { Text(value) }) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReportMetric("Calls", state.calls.count { it.startedAt >= start }, Modifier.weight(1f)); ReportMetric("Connected", connected, Modifier.weight(1f)); ReportMetric("Leads", state.leads.count { it.updatedAt >= start }, Modifier.weight(1f)); ReportMetric("Due", pendingFollowUps, Modifier.weight(1f)) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = { ReportExporter.shareCsv(context, reportTitle, export.headers, export.rows) }) { Icon(Icons.Outlined.Download, null); Text("  CSV") } }
            item { OutlinedButton(onClick = { ReportExporter.sharePdf(context, reportTitle, listOf(export.headers.joinToString(" | ")) + export.rows.map { it.joinToString(" | ") }) }) { Icon(Icons.Outlined.PictureAsPdf, null); Text("  PDF") } }
            item { OutlinedButton(onClick = { ReportExporter.shareSummary(context, reportTitle, export.rows.take(25).map { it.joinToString(": ") }) }) { Icon(Icons.Outlined.Share, null); Text("  SHARE") } }
        } }
        if (reportType == "Overview") {
            item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionHeader("Performance summary", range); AnalysisMetric("Connection rate", if (state.calls.count { it.startedAt >= start } == 0) "0%" else "${connected * 100 / state.calls.count { it.startedAt >= start }}%"); AnalysisMetric("Total talk time", formatDuration(state.calls.filter { it.startedAt >= start && it.answeredAt != null && it.endedAt != null }.sumOf { java.time.Duration.between(it.answeredAt, it.endedAt).seconds.coerceAtLeast(0) })); AnalysisMetric("Pending follow-ups", pendingFollowUps.toString()) } } }
            val funnelLeads = state.leads.filter { it.updatedAt >= start }
            val stages = listOf("New" to funnelLeads.size, "Contacted" to funnelLeads.count { it.stageId inStageOrBeyond 1 }, "Qualified" to funnelLeads.count { it.stageId inStageOrBeyond 2 }, "Proposal" to funnelLeads.count { it.stageId inStageOrBeyond 3 }, "Won" to funnelLeads.count { it.stageId inStageOrBeyond 4 })
            item { SectionHeader("Lead conversion funnel", "${funnelLeads.size} leads") }
            item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) { stages.forEachIndexed { index, (label, count) -> val previous = stages.getOrNull(index - 1)?.second ?: count; FunnelStage(label, count, funnelLeads.size, if (index == 0) 100 else if (previous == 0) 0 else count * 100 / previous, if (index == 0) 0 else previous - count) } } } }
            item { SectionHeader("Lead stage breakdown", "${funnelLeads.size} records") }
            val breakdown = funnelLeads.groupingBy { it.stageId }.eachCount().entries.sortedByDescending { it.value }
            if (breakdown.isEmpty()) item { EmptyReport() } else items(breakdown, key = { it.key }) { (stage, count) -> PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(stage.reportLabel(), fontWeight = FontWeight.SemiBold); Text(count.toString(), color = Indigo, fontWeight = FontWeight.Bold) } } }
        } else if (reportType == "Shift") {
            if (shiftLoading) item { PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp)); Text("Loading shift analytics…") } } }
            shiftError?.let { item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = viewModel::refreshShifts) { Text("TRY AGAIN") } } } } }
            shiftSummary?.let { summary ->
                item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionHeader("Today’s shift", summary.today.date); AnalysisMetric("Active time", formatDuration(summary.today.activeSeconds)); AnalysisMetric("Calls per active hour", summary.today.callsPerActiveHour.toString()); AnalysisMetric("First call", serverTime(summary.today.firstCallAt)); AnalysisMetric("Last call", serverTime(summary.today.lastCallAt)); Text(if (summary.today.shiftEndedAt == null && summary.today.shiftStartedAt != null) "Shift currently active" else if (summary.today.shiftEndedAt != null) "Shift ended ${serverTime(summary.today.shiftEndedAt)}" else "Shift not started", color = if (summary.today.shiftEndedAt == null && summary.today.shiftStartedAt != null) Emerald else Slate, fontWeight = FontWeight.SemiBold) } } }
                item { SectionHeader("Last 7 days", formatDuration(summary.totalActiveSeconds)) }
                items(summary.last7Days, key = { "shift-${it.date}" }) { day -> PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(day.date, fontWeight = FontWeight.Bold); Text(formatDuration(day.activeSeconds), color = Indigo, fontWeight = FontWeight.Bold) }; Text("${day.calls} calls · ${day.connected} connected · ${day.callsPerActiveHour}/hour", color = Slate); Text("First ${serverTime(day.firstCallAt)} · Last ${serverTime(day.lastCallAt)}", style = MaterialTheme.typography.bodySmall, color = Slate) } } }
            }
        } else {
            item { OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search $reportType records") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(statuses) { value -> FilterChip(status.equals(value, true), { status = value }, label = { Text(value.reportLabel()) }) } } }
            item { SectionHeader("$reportType records", when (reportType) { "Calls" -> "${calls.size} records"; "Leads" -> "${leads.size} records"; else -> "${followUps.size} records" }) }
            when (reportType) {
                "Calls" -> if (calls.isEmpty()) item { EmptyReport() } else items(calls, key = { "call-${it.id}" }) { call -> PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(call.phone, fontWeight = FontWeight.Bold); Text(formatter.format(call.startedAt), color = Slate); Text(call.direction.name.reportLabel(), style = MaterialTheme.typography.labelMedium) }; Text(if (call.answeredAt == null) "Missed" else formatDuration(call.endedAt?.epochSecond?.minus(call.answeredAt.epochSecond) ?: 0), color = if (call.answeredAt == null) MaterialTheme.colorScheme.error else Emerald) } } }
                "Leads" -> if (leads.isEmpty()) item { EmptyReport() } else items(leads, key = { "lead-${it.id}" }) { lead -> ReportLeadRow(lead) }
                else -> if (followUps.isEmpty()) item { EmptyReport() } else items(followUps, key = { "follow-${it.id}" }) { value -> PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatter.format(value.scheduledAt), fontWeight = FontWeight.Bold); Text(value.status.name.reportLabel(), color = if (value.status == FollowUpStatus.PENDING) Indigo else Slate) }; Text("Lead ${value.leadId}", color = Slate); value.note?.let { Text(it) } } } }
            }
        }
    }
}

private data class ExportData(val headers: List<String>, val rows: List<List<String>>)
private infix fun String.inStageOrBeyond(minimum: Int): Boolean { val index = when { contains("won", true) -> 4; contains("proposal", true) -> 3; contains("qualified", true) -> 2; contains("contacted", true) -> 1; else -> 0 }; return index >= minimum }
@Composable private fun FunnelStage(label: String, count: Int, total: Int, conversion: Int, dropOff: Int) = Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.Bold); Text("$count · $conversion%", color = Indigo, fontWeight = FontWeight.Bold) }; LinearProgressIndicator(progress = { if (total == 0) 0f else count.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(8.dp)); if (dropOff > 0) Text("$dropOff lead drop-off from previous stage", color = Slate, style = MaterialTheme.typography.labelSmall) }
private fun serverTime(value: String?) = value?.let { runCatching { formatter.format(Instant.parse(it)) }.getOrNull() } ?: "—"

@Composable private fun PageTitle(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
@Composable private fun ReportMetric(label: String, value: Int, modifier: Modifier = Modifier) = Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(12.dp)) { Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = Slate) } }
@Composable private fun EmptyReport() = PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("No records in this view", fontWeight = FontWeight.Bold); Text("Change the date, status or search filter.", color = Slate) } }
@Composable private fun ReportLeadRow(lead: Lead) = PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp)) { Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.Bold); Text(listOfNotNull(lead.company, lead.city).joinToString(" · "), color = Slate); Text(lead.displayPhone, color = Indigo) }; Text(lead.stageId.reportLabel(), style = MaterialTheme.typography.labelMedium) } }
private fun String.reportLabel() = replace('_', ' ').replace('-', ' ').lowercase().split(' ').filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

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

@Composable fun FollowUpsScreen(onOpenLead: (String) -> Unit, onCallLead: (String) -> Unit, viewModel: FollowUpsViewModel = hiltViewModel()) {
    val values by viewModel.followUps.collectAsStateWithLifecycle()
    val leads by viewModel.leads.collectAsStateWithLifecycle()
    val completingIds by viewModel.completingIds.collectAsStateWithLifecycle()
    val completionError by viewModel.completionError.collectAsStateWithLifecycle()
    val checkedInIds by viewModel.checkedInIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }
    var pendingCheckIn by remember { mutableStateOf<com.callflow.app.core.model.FollowUpRecord?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions -> if (permissions.values.any { it }) pendingCheckIn?.let(viewModel::checkIn); pendingCheckIn = null }
    var filter by remember { mutableStateOf("Today") }
    var editing by remember { mutableStateOf<com.callflow.app.core.model.FollowUpRecord?>(null) }
    var editNote by remember { mutableStateOf("") }
    var editAt by remember { mutableStateOf(Instant.now().plusSeconds(3600)) }
    val now = Instant.now()
    val today = LocalDate.now()
    val pending = values.filter { value ->
        if (value.status != FollowUpStatus.PENDING) return@filter false
        val date = value.scheduledAt.atZone(ZoneId.systemDefault()).toLocalDate()
        when (filter) {
            "Overdue" -> value.scheduledAt < now
            "Upcoming" -> date > today
            else -> date == today
        }
    }
    editing?.let { value ->
        val dateTime = editAt.atZone(ZoneId.systemDefault())
        AlertDialog(
            onDismissRequest = { if (value.id !in completingIds) editing = null },
            title = { Text("Manage follow-up") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(leads.firstOrNull { it.id == value.leadId }?.name ?: "Assigned lead", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        val current = editAt.atZone(ZoneId.systemDefault())
                        TimePickerDialog(context, { _, hour, minute -> editAt = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant() }, current.hour, current.minute, false).show()
                    }, dateTime.year, dateTime.monthValue - 1, dateTime.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) { Text(formatter.format(editAt)) }
                OutlinedTextField(editNote, { editNote = it }, label = { Text("Follow-up note") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { viewModel.cancel(value.id); editing = null }, enabled = value.id !in completingIds, modifier = Modifier.fillMaxWidth()) { Text("CANCEL FOLLOW-UP", color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button(onClick = { viewModel.update(value.id, editAt, editNote); editing = null }, enabled = editAt.isAfter(Instant.now()) && value.id !in completingIds) { Text("SAVE CHANGES") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("CLOSE") } },
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Follow-ups", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        if (!notificationsAllowed) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Follow-up reminders are off", fontWeight = FontWeight.SemiBold); Text("Allow notifications to receive due and overdue reminders even when CallFlow is in the background."); Button(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("ALLOW REMINDERS") } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Today", "Overdue", "Upcoming").forEach { value -> FilterChip(filter == value, { filter = value }, label = { Text(value) }) } } }
        completionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        if (pending.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("No $filter follow-ups", fontWeight = FontWeight.SemiBold); Text("You’re clear in this view. New reminders will appear automatically after sync.", color = Slate) } } }
        items(pending, key = { it.id }) { value ->
            val lead = leads.firstOrNull { it.id == value.leadId }
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(lead?.name ?: "Assigned lead", fontWeight = FontWeight.Bold); Text(formatter.format(value.scheduledAt), color = Indigo, fontWeight = FontWeight.SemiBold); value.note?.let { Text(it, color = Slate) } }; Button(onClick = { viewModel.complete(value.id) }, enabled = value.id !in completingIds) { Text(if (value.id in completingIds) "Saving…" else "Done") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { onOpenLead(value.leadId) }) { Text("OPEN") }; TextButton(onClick = { onCallLead(value.leadId) }, enabled = lead != null) { Text("CALL") }; if (value.type.equals("MEETING", true)) TextButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) viewModel.checkIn(value) else { pendingCheckIn = value; locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) } }, enabled = value.id !in completingIds && value.id !in checkedInIds) { Icon(Icons.Outlined.LocationOn, null); Text(if (value.id in checkedInIds) " CHECKED IN" else " CHECK IN") }; TextButton(onClick = { editing = value; editNote = value.note.orEmpty(); editAt = value.scheduledAt }) { Text("EDIT") } }
            } }
        }
    }
}

@Composable fun MoreScreen(employeeName: String, employeePhone: String?, onProfile: () -> Unit, onReports: () -> Unit, onTeamContent: () -> Unit, onSettings: () -> Unit) {
    val legacyPhone = employeeName.filter(Char::isDigit).takeIf { it.length >= 10 }
    val profileName = if (legacyPhone != null && employeePhone.isNullOrBlank()) "Salesperson" else employeeName.ifBlank { "Salesperson" }
    val profilePhone = employeePhone?.ifBlank { null } ?: legacyPhone
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("More", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Profile, reports and work settings", color = Slate) }
        item { PremiumCard(Modifier.fillMaxWidth().clickable(onClick = onProfile)) { Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profileName)
            Column(Modifier.weight(1f)) { Text(profileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(profilePhone ?: "Mobile number unavailable", color = Slate); Text("View profile", color = Indigo, style = MaterialTheme.typography.labelMedium) }
            Icon(Icons.Outlined.ChevronRight, "Open profile")
        } } }
        item { MenuRow("Reports", "All calls, leads and follow-up records with filters", Icons.Outlined.Assessment, onReports) }
        item { MenuRow("Team Hub", "Announcements and approved call scripts", Icons.Outlined.Campaign, onTeamContent) }
        item { MenuRow("Settings", "Lead assignment, sync, permissions and account", Icons.Outlined.Settings, onSettings) }
    }
}

@Composable fun TeamContentScreen(onBack: () -> Unit, viewModel: TeamContentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle(); val refreshing by viewModel.refreshing.collectAsStateWithLifecycle(); var tab by remember { mutableStateOf("Announcements") }; val rows = if (tab == "Announcements") state.announcements else state.scripts
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Team Hub", onBack); Text("Synced updates and approved talking points from your manager.", color = Slate) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(tab == "Announcements", { tab = "Announcements" }, label = { Text("Announcements") }); FilterChip(tab == "Scripts", { tab = "Scripts" }, label = { Text("Call Scripts") }) } }
        item { OutlinedButton(onClick = viewModel::refresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) { Text(if (refreshing) "SYNCING…" else "SYNC LATEST CONTENT") } }
        if (rows.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(if (tab == "Announcements") Icons.Outlined.Campaign else Icons.Outlined.Description, null, tint = Indigo); Text("No ${tab.lowercase()} published", fontWeight = FontWeight.Bold); Text("New content from the dashboard will appear here after sync.", color = Slate) } } }
        items(rows, key = { it.id }) { item -> PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)) { Text(item.category.uppercase(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }; Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable private fun ProfileAvatar(name: String) = Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(58.dp)) { androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text(name.trim().take(1).uppercase().ifBlank { "U" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } }

@Composable private fun MenuRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) = PremiumCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(48.dp)) { Icon(icon, null, tint = Indigo, modifier = Modifier.padding(12.dp)) }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(subtitle, color = Slate, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Outlined.ChevronRight, "Open $title") } }

@Composable fun ProfileScreen(employeeName: String, employeePhone: String?, onBack: () -> Unit) {
    val legacyPhone = employeeName.filter(Char::isDigit).takeIf { it.length >= 10 }
    val name = if (legacyPhone != null && employeePhone.isNullOrBlank()) "Salesperson" else employeeName.ifBlank { "Salesperson" }
    val phone = employeePhone?.ifBlank { null } ?: legacyPhone
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("My profile", onBack) }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { ProfileAvatar(name); Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(phone ?: "Mobile number unavailable", color = Slate); Text("Sales team member", color = Indigo, style = MaterialTheme.typography.labelLarge) } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Account information", fontWeight = FontWeight.Bold); Text("Name", color = Slate, style = MaterialTheme.typography.labelMedium); Text(name); Text("Mobile", color = Slate, style = MaterialTheme.typography.labelMedium); Text(phone ?: "Not available") } } }
    }
}

@Composable fun SettingsScreen(onBack: () -> Unit, viewModel: SyncStatusViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val availability by viewModel.assignmentAvailability.collectAsStateWithLifecycle()
    var confirmEndShift by remember { mutableStateOf(false) }
    var pendingShiftState by remember { mutableStateOf<Boolean?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions -> if (permissions.values.any { it }) pendingShiftState?.let(viewModel::setAcceptingLeads); pendingShiftState = null }
    fun changeShift(accepting: Boolean) { if (viewModel.hasLocationPermission()) viewModel.setAcceptingLeads(accepting) else { pendingShiftState = accepting; locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) } }
    if (confirmEndShift) AlertDialog(
        onDismissRequest = { if (!availability.saving) confirmEndShift = false },
        title = { Text("End shift and pause new leads?") },
        text = { Text("Your current leads will stay in the app. The dashboard will stop auto-assigning new leads to you until you resume assignments.") },
        confirmButton = { TextButton(enabled = !availability.saving, onClick = { changeShift(false); confirmEndShift = false }) { Text("END SHIFT") } },
        dismissButton = { TextButton(enabled = !availability.saving, onClick = { confirmEndShift = false }) { Text("CANCEL") } }
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Settings", onBack) }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lead assignment status", fontWeight = FontWeight.SemiBold)
            Text(when { availability.loading -> "Checking your shift status…"; availability.acceptingLeads == true -> "On duty · New leads can be assigned"; availability.acceptingLeads == false -> "Off duty · New lead assignments are paused"; else -> "Status unavailable" }, fontWeight = FontWeight.Bold, color = if (availability.acceptingLeads == true) Emerald else Slate)
            Text(when (availability.acceptingLeads) { true -> "End your shift before leave or time off. Your existing leads remain available."; false -> "Resume when you are ready to receive new dashboard leads."; null -> "No assignment status change has been made." }, color = Slate, style = MaterialTheme.typography.bodySmall)
            availability.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (availability.error != null) OutlinedButton(onClick = viewModel::refreshAssignmentAvailability, enabled = !availability.loading) { Text("TRY AGAIN") }
            else if (!availability.loading && availability.acceptingLeads != null) Button(onClick = { if (availability.acceptingLeads == true) confirmEndShift = true else changeShift(true) }, enabled = !availability.saving) { Icon(Icons.Outlined.LocationOn, null); Text(if (availability.saving) "  CAPTURING LOCATION…" else if (availability.acceptingLeads == true) "  END SHIFT" else "  RESUME ASSIGNMENTS") }
            Text("Location is captured only when you start/end a shift or check in to a meeting. Continuous tracking is off.", color = Slate, style = MaterialTheme.typography.bodySmall)
        } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Sync status", fontWeight = FontWeight.SemiBold); Text(if (pending == 0) "Everything is up to date" else "$pending changes waiting to sync"); health.lastSuccessfulAt?.let { Text("Last synced ${formatter.format(it)}", color = Slate) }; health.lastError?.let { Text("Last error: $it", color = MaterialTheme.colorScheme.error) }; if (conflicts > 0) Text("$conflicts conflicts require support review", color = MaterialTheme.colorScheme.error); Text(if (BuildConfig.USE_FAKE_BACKEND) "Local testing environment" else "Connected to Coach For Life CRM", color = Slate); Button(onClick = viewModel::retry, enabled = !syncing) { Text(if (syncing) "SYNCING…" else "SYNC NOW") } } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Call permissions", fontWeight = FontWeight.SemiBold); Text("Phone tracking: ${permissionLabel(viewModel.permissions.callTracking)}"); Text("Notifications: ${permissionLabel(viewModel.permissions.notifications)}"); Text("Place calls: ${permissionLabel(viewModel.permissions.calling)}"); Text("Permissions are used only for business calling and automatic call reports.", color = Slate, style = MaterialTheme.typography.bodySmall) } } }
        item { OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("SIGN OUT") } }
    }
}

private fun permissionLabel(value: PermissionState) = when (value) {
    PermissionState.GRANTED -> "Enabled"
    PermissionState.DENIED -> "Not allowed"
    PermissionState.PERMANENTLY_DENIED -> "Disabled in Android settings"
    PermissionState.NOT_REQUIRED -> "Available"
    PermissionState.ROLE_MISSING -> "Not enabled"
}
