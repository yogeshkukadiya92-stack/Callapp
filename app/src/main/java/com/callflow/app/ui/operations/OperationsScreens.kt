package com.callflow.app.ui.operations

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable fun CallsScreen(initialFilter: String = "All", initialDirectionFilter: String = "All directions", initialRange: String = "7 days", uniqueOnly: Boolean = false, onDialNumber: () -> Unit = {}, onOpenCall: (String) -> Unit = {}, onAddNote: (String) -> Unit = onOpenCall, viewModel: CallsViewModel = hiltViewModel()) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val refreshingCallHistory by viewModel.refreshingCallHistory.collectAsStateWithLifecycle()
    val leadNames by viewModel.leadNames.collectAsStateWithLifecycle()
    val contactNames by viewModel.contactNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }
    var showAnalysis by remember { mutableStateOf(false) }
    var showExtraFilters by remember { mutableStateOf(false) }
    var range by remember(initialRange) { mutableStateOf(initialRange) }
    var simFilter by remember { mutableStateOf("All SIMs") }
    var directionFilter by remember(initialDirectionFilter) { mutableStateOf(initialDirectionFilter) }
    var durationFilter by remember { mutableStateOf("Any duration") }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        com.callflow.app.sync.SyncWorker.syncNow(context)
    }
    val hasCallLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(hasCallLogPermission) {
        if (hasCallLogPermission) viewModel.refreshCallHistory()
    }
    val rangeStart = remember(range) {
        val days = when (range) { "Today" -> 1L; "30 days" -> 30L; "Custom" -> null; else -> 7L }
        days?.let { LocalDate.now().minusDays(it - 1).atStartOfDay(ZoneId.systemDefault()).toInstant() } ?: Instant.EPOCH
    }
    val simOptions = remember(calls) { listOf("All SIMs") + calls.map { it.simDisplay() }.distinct().sorted() }
    val matchingCalls = calls.filter { call ->
        val localDay = call.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val seconds = call.talkSeconds()
        val selectedFrom = fromDate
        val selectedTo = toDate
        call.startedAt >= rangeStart && (range != "Custom" || (selectedFrom == null || localDay >= selectedFrom) && (selectedTo == null || localDay <= selectedTo)) &&
            (filter == "All" || filter == "Connected" && call.status == CallStatus.CONNECTED || filter == "Missed" && call.status == CallStatus.MISSED || filter == "Not connected" && call.status == CallStatus.NOT_CONNECTED || filter == "Unmatched" && call.leadId == null) &&
            (call.phone.contains(query, ignoreCase = true) || call.leadId?.let(leadNames::get)?.contains(query, ignoreCase = true) == true || contactNames[call.phone]?.contains(query, ignoreCase = true) == true) && (simFilter == "All SIMs" || call.simDisplay() == simFilter) &&
            (directionFilter == "All directions" || call.direction.name.equals(directionFilter, true)) &&
            (durationFilter == "Any duration" || durationFilter == "Under 1 min" && seconds in 1..59 || durationFilter == "1–5 min" && seconds in 60..300 || durationFilter == "5+ min" && seconds > 300 || durationFilter == "No talk" && seconds == 0L)
    }
    val visibleCalls = if (uniqueOnly) matchingCalls.distinctBy { it.phone } else matchingCalls
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Call History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your conversations, in one place", color = Slate, style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onDialNumber,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Indigo.copy(alpha = 0.16f), contentColor = Indigo),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Outlined.Call, null, modifier = Modifier.size(16.dp))
                    Text("  Dial Pad", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                Surface(
                    onClick = { showAnalysis = false },
                    shape = RoundedCornerShape(10.dp),
                    color = if (!showAnalysis) Indigo else Color.Transparent,
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Calls History",
                            fontWeight = if (!showAnalysis) FontWeight.Bold else FontWeight.Medium,
                            color = if (!showAnalysis) Color.White else Slate,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Surface(
                    onClick = { showAnalysis = true },
                    shape = RoundedCornerShape(10.dp),
                    color = if (showAnalysis) Indigo else Color.Transparent,
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Analytics",
                            fontWeight = if (showAnalysis) FontWeight.Bold else FontWeight.Medium,
                            color = if (showAnalysis) Color.White else Slate,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(listOf("Today", "7 days", "30 days", "Custom")) { value ->
                    FilterChip(
                        selected = range == value,
                        onClick = { range = value },
                        label = { Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = if (range == value) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
        if (range == "Custom") item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showCallDatePicker(context, fromDate ?: LocalDate.now()) { fromDate = it; if (toDate != null && it > toDate) toDate = it } }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(fromDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: "FROM", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = { showCallDatePicker(context, toDate ?: LocalDate.now()) { toDate = it; if (fromDate != null && it < fromDate) fromDate = it } }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(toDate?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: "TO", style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (showAnalysis) {
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    DonutChart(analysis.connectionRatePercent, Modifier.size(78.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        AnalysisSummaryRow("Total calls", analysis.totalCalls.toString())
                        AnalysisSummaryRow("Average talk", formatDuration(analysis.averageTalkTimeSeconds))
                        AnalysisSummaryRow("Total talk", formatDuration(analysis.totalTalkTimeSeconds))
                    }
                }
            }
        }
        item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnalysisHeading("Outcome breakdown", "Selected calls")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalysisMetric("Connected", analysis.connectedCalls.toString(), Modifier.weight(1f))
                    AnalysisMetric("Missed", analysis.missedCalls.toString(), Modifier.weight(1f))
                    AnalysisMetric("Not connected", analysis.notConnectedCalls.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnalysisMetric("Incoming", analysis.incomingCalls.toString(), Modifier.weight(1f))
                    AnalysisMetric("Outgoing", analysis.outgoingCalls.toString(), Modifier.weight(1f))
                    AnalysisMetric("Unique", analysis.uniqueNumbers.toString(), Modifier.weight(1f))
                }
            } }
        }
        if (visibleCalls.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnalysisHeading("Performance insights", "Automatic")
                Text("Peak: ${analysis.peakHour?.let(::hourLabel) ?: "—"} · ${analysis.peakHourCalls} calls", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Longest: ${formatDuration(analysis.longestTalkTimeSeconds)}", color = Slate, style = MaterialTheme.typography.bodySmall)
                Text(callInsight(analysis), color = Indigo, style = MaterialTheme.typography.bodySmall)
            } }
        }
        if (visibleCalls.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnalysisHeading("Peak calling hours", range)
                BarChart(hourlyCalls, Modifier.fillMaxWidth().height(76.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("9a", "11a", "1p", "3p", "5p", "7p").forEach { Text(it, color = Slate, style = MaterialTheme.typography.labelSmall) } }
            } }
        }
        if (visibleCalls.isNotEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnalysisHeading("Call trend", range)
                ActivityChart(dailyCalls, labels = dailyLabels, chartHeight = 80.dp)
            } }
        }
        }
        item { AnalysisHeading(if (uniqueOnly) "Unique calls" else "Call history", "${visibleCalls.size} calls") }
        item { OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(17.dp)) }, trailingIcon = if (query.isNotEmpty()) {{ androidx.compose.material3.IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search", modifier = Modifier.size(17.dp)) } }} else null, placeholder = { Text("Search calls") }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) }
        item { CompactFilterRow(listOf("All", "Connected", "Missed", "Not connected", "Unmatched"), filter) { filter = it } }
        item { TextButton(onClick = { showExtraFilters = !showExtraFilters }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(if (showExtraFilters) "Hide filters" else "More filters · Direction, duration, SIM", style = MaterialTheme.typography.labelMedium) } }
        if (showExtraFilters) {
        item { CompactFilterRow(listOf("All directions", "Incoming", "Outgoing"), directionFilter) { directionFilter = it } }
        item { CompactFilterRow(listOf("Any duration", "No talk", "Under 1 min", "1–5 min", "5+ min"), durationFilter) { durationFilter = it } }
        item { CompactFilterRow(simOptions, simFilter) { simFilter = it } }
        }
        if (calls.any { it.leadId == null }) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("${calls.count { it.leadId == null }} unmatched calls · Included in reports", color = Slate, style = MaterialTheme.typography.labelSmall); if (filter == "Unmatched") Text("When a matching lead is assigned from the dashboard, CallFlow links the history automatically.", color = Slate, style = MaterialTheme.typography.labelSmall) } } }
        if (visibleCalls.isEmpty()) item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = (if (!hasCallLogPermission) Indigo else Emerald).copy(alpha = 0.14f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (!hasCallLogPermission) Icons.Outlined.PhoneAndroid else Icons.Outlined.Call,
                                contentDescription = null,
                                tint = if (!hasCallLogPermission) Indigo else Emerald,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Text(
                        if (!hasCallLogPermission) "Call Tracking Not Enabled" else "No calls in this view",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (!hasCallLogPermission) "Allow call-log permission so calls made with your phone's dialer sync here automatically."
                        else "Make or receive calls using your phone's dialer on your selected SIM. Completed calls will sync here automatically.",
                        color = Slate,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (!hasCallLogPermission) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val permissions = buildList {
                                    add(Manifest.permission.READ_CALL_LOG)
                                    add(Manifest.permission.READ_PHONE_STATE)
                                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                }.toTypedArray()
                                permissionLauncher.launch(permissions)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                            Text("  ENABLE CALL TRACKING", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = viewModel::refreshCallHistory, enabled = !refreshingCallHistory) {
                            if (refreshingCallHistory) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(if (refreshingCallHistory) "  Checking call history…" else "  Refresh call history")
                        }
                        TextButton(onClick = viewModel::syncImportedCalls) {
                            Text("Sync to dashboard")
                        }
                    }
                }
            }
        }
        items(visibleCalls, key = { it.id }) { call ->
            PremiumCard(Modifier.fillMaxWidth().clickable { onOpenCall(call.id) }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Call, null, tint = if (call.status == CallStatus.MISSED) MaterialTheme.colorScheme.error else Indigo, modifier = Modifier.padding(8.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        val callerTitle = call.leadId?.let(leadNames::get) ?: contactNames[call.phone] ?: call.phone
                        Text(callerTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (callerTitle != call.phone) Text(call.phone, color = Slate, style = MaterialTheme.typography.bodySmall)
                        Text(call.direction.name.lowercase().replaceFirstChar(Char::uppercase), color = Slate, style = MaterialTheme.typography.labelSmall)
                        Row(
                            Modifier.padding(top = 4.dp).clickable { onAddNote(call.id) }.padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Description, null, Modifier.size(15.dp), tint = Slate)
                            Text("Tap to add note", color = Slate, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatter.format(call.startedAt), color = Slate, style = MaterialTheme.typography.labelSmall)
                        Text(if (call.status == CallStatus.CONNECTED) formatDuration(call.talkSeconds()) else if (call.status == CallStatus.MISSED) "Missed" else "Not connected", color = if (call.status == CallStatus.CONNECTED) Emerald else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        Text(call.simDisplay(), color = Slate, style = MaterialTheme.typography.labelSmall)
                        Text("Details ›", color = Indigo, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFilterRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(options) { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(value, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(30.dp),
            )
        }
    }
}

@Composable
private fun AnalysisSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Slate, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

private fun showCallDatePicker(context: android.content.Context, initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

private fun com.callflow.app.core.model.CallRecord.talkSeconds(): Long = answeredAt?.let { start -> endedAt?.epochSecond?.minus(start.epochSecond) }?.coerceAtLeast(0) ?: 0

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
    var reportSim by remember { mutableStateOf("All SIMs") }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    val start = remember(range) { when (range) { "Today" -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(); "7 days" -> LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant(); "30 days" -> LocalDate.now().minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant(); else -> Instant.EPOCH } }
    val rangeCalls = state.calls.filter { call ->
        val day = call.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        call.startedAt >= start && (range != "Custom" || (fromDate == null || day >= fromDate) && (toDate == null || day <= toDate))
    }
    val performance = remember(rangeCalls) { CallAnalysisCalculator.calculate(rangeCalls) }
    val dailyPerformance = remember(rangeCalls) { rangeCalls.groupBy { it.startedAt.atZone(ZoneId.systemDefault()).toLocalDate() }.toSortedMap(compareByDescending { it }).map { (day, rows) -> day to CallAnalysisCalculator.calculate(rows) } }
    val reportSimOptions = remember(state.calls) { listOf("All SIMs") + state.calls.map { it.simDisplay() }.distinct().sorted() }
    val calls = rangeCalls.filter { (status == "All" || status == "Connected" && it.status == CallStatus.CONNECTED || status == "Missed" && it.status == CallStatus.MISSED || status == "Not connected" && it.status == CallStatus.NOT_CONNECTED) && it.phone.contains(query, true) && (reportSim == "All SIMs" || it.simDisplay() == reportSim) }
    val leads = state.leads.filter { it.updatedAt >= start && (status == "All" || status == "New" && it.stageId.contains("new", true) || status == "Old" && !it.stageId.contains("new", true) || it.stageId.equals(status, true)) && listOf(it.name, it.displayPhone, it.company.orEmpty(), it.city.orEmpty()).any { value -> value.contains(query, true) } }
    val followUps = state.followUps.filter { it.scheduledAt >= start && (status == "All" || it.status.name.equals(status, true)) && (it.note.orEmpty().contains(query, true) || it.leadId.contains(query, true)) }
    val connected = rangeCalls.count { it.answeredAt != null }
    val pendingFollowUps = state.followUps.count { it.scheduledAt >= start && it.status == FollowUpStatus.PENDING }
    val stageOptions = state.leads.map { it.stageId }.distinct().sorted()
    val statuses = when (reportType) { "Calls" -> listOf("All", "Connected", "Missed", "Not connected"); "Leads" -> (listOf("All", "New", "Old") + stageOptions).distinct(); "Follow-ups" -> listOf("All", "Pending", "Completed", "Cancelled", "Rescheduled", "Missed"); else -> emptyList() }
    val totalInRange = rangeCalls.size
    val reportTitle = "CallFlow $reportType · $range${if (status == "All" || reportType == "Overview") "" else " · $status"}"
    val export = remember(reportType, range, status, query, calls, leads, followUps, connected, pendingFollowUps, shiftSummary) {
        when (reportType) {
            "Calls" -> ExportData(listOf("Phone", "Date", "Direction", "SIM", "Status", "Duration"), calls.map { call -> listOf(call.phone, formatter.format(call.startedAt), call.direction.name.reportLabel(), call.simDisplay(), call.status.name.reportLabel(), if (call.answeredAt == null) "0s" else formatDuration(call.endedAt?.epochSecond?.minus(call.answeredAt.epochSecond) ?: 0)) })
            "Performance" -> ExportData(listOf("Date", "Calls", "Connected", "Missed", "Not connected", "Connection rate", "Talk time", "Average talk", "Unique numbers"), dailyPerformance.map { (day, value) -> listOf(day.toString(), value.totalCalls.toString(), value.connectedCalls.toString(), value.missedCalls.toString(), value.notConnectedCalls.toString(), "${value.connectionRatePercent}%", formatDuration(value.totalTalkTimeSeconds), formatDuration(value.averageTalkTimeSeconds), value.uniqueNumbers.toString()) })
            "Leads" -> ExportData(listOf("Name", "Phone", "Company", "City", "Stage", "Updated"), leads.map { lead -> listOf(lead.name, lead.displayPhone, lead.company.orEmpty(), lead.city.orEmpty(), lead.stageId.reportLabel(), formatter.format(lead.updatedAt)) })
            "Follow-ups" -> ExportData(listOf("Lead ID", "Scheduled", "Status", "Priority", "Note"), followUps.map { value -> listOf(value.leadId, formatter.format(value.scheduledAt), value.status.name.reportLabel(), value.priority.toString(), value.note.orEmpty()) })
            "Shift" -> ExportData(listOf("Date", "Shift start", "Shift end", "Active time", "Calls", "Connected", "First call", "Last call", "Calls/hour"), shiftSummary?.last7Days.orEmpty().map { day -> listOf(day.date, serverTime(day.shiftStartedAt), serverTime(day.shiftEndedAt), formatDuration(day.activeSeconds), day.calls.toString(), day.connected.toString(), serverTime(day.firstCallAt), serverTime(day.lastCallAt), day.callsPerActiveHour.toString()) })
            else -> ExportData(listOf("Metric", "Value"), listOf(listOf("Total calls", totalInRange.toString()), listOf("Connected", connected.toString()), listOf("Connection rate", "${performance.connectionRatePercent}%"), listOf("Talk time", formatDuration(performance.totalTalkTimeSeconds)), listOf("Unique numbers", performance.uniqueNumbers.toString()), listOf("Leads", state.leads.count { it.updatedAt >= start }.toString()), listOf("Pending follow-ups", pendingFollowUps.toString())) + state.leads.filter { it.updatedAt >= start }.groupingBy { it.stageId }.eachCount().map { listOf("Stage: ${it.key.reportLabel()}", it.value.toString()) })
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Reports", onBack); Text("All synced sales activity in one place", color = Slate) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Overview", "Performance", "Calls", "Leads", "Follow-ups", "Shift")) { value -> FilterChip(reportType == value, { reportType = value }, label = { Text(value) }) } } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Today", "7 days", "30 days", "Custom", "All time")) { value -> FilterChip(range == value, { range = value }, label = { Text(value) }) } } }
        if (range == "Custom") item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCallDatePicker(context, fromDate ?: LocalDate.now()) { fromDate = it; if (toDate != null && it > toDate) toDate = it } }, modifier = Modifier.weight(1f)) { Text(fromDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "FROM DATE") }
            OutlinedButton(onClick = { showCallDatePicker(context, toDate ?: LocalDate.now()) { toDate = it; if (fromDate != null && it < fromDate) fromDate = it } }, modifier = Modifier.weight(1f)) { Text(toDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "TO DATE") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ReportMetric("Calls", totalInRange, Modifier.weight(1f)); ReportMetric("Connected", connected, Modifier.weight(1f)); ReportMetric("Leads", state.leads.count { it.updatedAt >= start }, Modifier.weight(1f)); ReportMetric("Due", pendingFollowUps, Modifier.weight(1f)) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = { ReportExporter.shareCsv(context, reportTitle, export.headers, export.rows) },enabled=state.exportAllowed) { Icon(Icons.Outlined.Download, null); Text("  CSV") } }
            item { OutlinedButton(onClick = { ReportExporter.sharePdf(context, reportTitle, listOf(export.headers.joinToString(" | ")) + export.rows.map { it.joinToString(" | ") }) },enabled=state.exportAllowed) { Icon(Icons.Outlined.PictureAsPdf, null); Text("  PDF") } }
            item { OutlinedButton(onClick = { ReportExporter.shareSummary(context, reportTitle, export.rows.take(25).map { it.joinToString(": ") }) },enabled=state.exportAllowed) { Icon(Icons.Outlined.Share, null); Text("  SHARE") } }
        } }
        if(!state.exportAllowed)item { Text("Subscription expired. Reports remain visible, but export and sharing are disabled until renewal.",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.SemiBold) }
        if (reportType == "Overview") {
            item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionHeader("Performance summary", range); AnalysisMetric("Connection rate", if (state.calls.count { it.startedAt >= start } == 0) "0%" else "${connected * 100 / state.calls.count { it.startedAt >= start }}%"); AnalysisMetric("Total talk time", formatDuration(state.calls.filter { it.startedAt >= start && it.answeredAt != null && it.endedAt != null }.sumOf { java.time.Duration.between(it.answeredAt, it.endedAt).seconds.coerceAtLeast(0) })); AnalysisMetric("Pending follow-ups", pendingFollowUps.toString()) } } }
            val funnelLeads = state.leads.filter { it.updatedAt >= start }
            val stages = listOf("New" to funnelLeads.size, "Contacted" to funnelLeads.count { it.stageId inStageOrBeyond 1 }, "Qualified" to funnelLeads.count { it.stageId inStageOrBeyond 2 }, "Proposal" to funnelLeads.count { it.stageId inStageOrBeyond 3 }, "Won" to funnelLeads.count { it.stageId inStageOrBeyond 4 })
            item { SectionHeader("Lead conversion funnel", "${funnelLeads.size} leads") }
            item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) { stages.forEachIndexed { index, (label, count) -> val previous = stages.getOrNull(index - 1)?.second ?: count; FunnelStage(label, count, funnelLeads.size, if (index == 0) 100 else if (previous == 0) 0 else count * 100 / previous, if (index == 0) 0 else previous - count) } } } }
            item { SectionHeader("Lead stage breakdown", "${funnelLeads.size} records") }
            val breakdown = funnelLeads.groupingBy { it.stageId }.eachCount().entries.sortedByDescending { it.value }
            if (breakdown.isEmpty()) item { EmptyReport() } else items(breakdown, key = { it.key }) { (stage, count) -> PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(stage.reportLabel(), fontWeight = FontWeight.SemiBold); Text(count.toString(), color = Indigo, fontWeight = FontWeight.Bold) } } }
        } else if (reportType == "Performance") {
            item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Call productivity", range)
                AnalysisMetric("Connection rate", "${performance.connectionRatePercent}%")
                AnalysisMetric("Total talk time", formatDuration(performance.totalTalkTimeSeconds))
                AnalysisMetric("Average conversation", formatDuration(performance.averageTalkTimeSeconds))
                AnalysisMetric("Unique contacts", performance.uniqueNumbers.toString())
                Text(callInsight(performance), color = Indigo, fontWeight = FontWeight.SemiBold)
            } } }
            item { SectionHeader("Daily performance", "${dailyPerformance.size} days") }
            if (dailyPerformance.isEmpty()) item { EmptyReport() } else items(dailyPerformance, key = { "performance-${it.first}" }) { (day, value) ->
                PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(day.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), fontWeight = FontWeight.Bold); Text("${value.connectionRatePercent}%", color = Indigo, fontWeight = FontWeight.Bold) }
                    Text("${value.totalCalls} calls · ${value.connectedCalls} connected · ${value.missedCalls} missed · ${value.notConnectedCalls} not connected", color = Slate)
                    Text("${formatDuration(value.totalTalkTimeSeconds)} talk · ${value.uniqueNumbers} unique numbers", style = MaterialTheme.typography.bodySmall, color = Slate)
                } }
            }
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
            if (reportType == "Calls") item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(reportSimOptions) { value -> FilterChip(reportSim == value, { reportSim = value }, label = { Text(value) }) } } }
            item { SectionHeader("$reportType records", when (reportType) { "Calls" -> "${calls.size} records"; "Leads" -> "${leads.size} records"; else -> "${followUps.size} records" }) }
            when (reportType) {
                "Calls" -> if (calls.isEmpty()) item { EmptyReport() } else items(calls, key = { "call-${it.id}" }) { call -> PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(call.phone, fontWeight = FontWeight.Bold); Text(formatter.format(call.startedAt), color = Slate); Text("${call.direction.name.reportLabel()} · ${call.simDisplay()}", style = MaterialTheme.typography.labelMedium, color = Indigo) }; Text(if (call.answeredAt == null) "Missed" else formatDuration(call.endedAt?.epochSecond?.minus(call.answeredAt.epochSecond) ?: 0), color = if (call.answeredAt == null) MaterialTheme.colorScheme.error else Emerald) } } }
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
private fun com.callflow.app.core.model.CallRecord.simDisplay(): String = when {
    simSlot != null && !simLabel.isNullOrBlank() && !simLabel.equals("SIM $simSlot", ignoreCase = true) -> "SIM $simSlot · $simLabel"
    simSlot != null -> "SIM $simSlot"
    !simLabel.isNullOrBlank() -> simLabel
    !phoneAccountId.isNullOrBlank() -> "SIM account $phoneAccountId"
    else -> "SIM unknown"
}

@Composable private fun PageTitle(title: String, onBack: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
@Composable private fun ReportMetric(label: String, value: Int, modifier: Modifier = Modifier) = Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(12.dp)) { Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = Slate) } }
@Composable private fun EmptyReport() = PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("No records in this view", fontWeight = FontWeight.Bold); Text("Change the date, status or search filter.", color = Slate) } }
@Composable private fun ReportLeadRow(lead: Lead) = PremiumCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp)) { Column(Modifier.weight(1f)) { Text(lead.name, fontWeight = FontWeight.Bold); Text(listOfNotNull(lead.company, lead.city).joinToString(" · "), color = Slate); Text(lead.displayPhone, color = Indigo) }; Text(lead.stageId.reportLabel(), style = MaterialTheme.typography.labelMedium) } }
private fun String.reportLabel() = replace('_', ' ').replace('-', ' ').lowercase().split(' ').filter(String::isNotBlank).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

@Composable private fun AnalysisMetric(label: String, value: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = Slate)
}

@Composable private fun AnalysisHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Slate)
    }
}

internal fun hourLabel(hour: Int): String {
    val normalized = hour.coerceIn(0, 23)
    val suffix = if (normalized < 12) "AM" else "PM"
    val display = when (val value = normalized % 12) { 0 -> 12; else -> value }
    return "$display:00 $suffix"
}

internal fun callInsight(analysis: com.callflow.app.core.call.CallAnalysis): String = when {
    analysis.totalCalls == 0 -> "Start calling to unlock performance insights."
    analysis.connectionRatePercent < 30 -> "Low connection rate — retry unanswered leads during the peak hour."
    analysis.averageTalkTimeSeconds < 30 -> "Calls are short — use the approved opening script and qualify the lead."
    analysis.notConnectedCalls > analysis.connectedCalls -> "Prioritize never-connected leads in the next calling session."
    else -> "Healthy calling pattern — continue follow-ups while interest is fresh."
}

internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return if (minutes > 0) "${minutes}m ${remainder}s" else "${remainder}s"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Today", "Overdue", "Upcoming")) { value -> FilterChip(filter == value, { filter = value }, label = { Text(value, maxLines = 1, softWrap = false) }) } } }
        completionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        if (pending.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("No $filter follow-ups", fontWeight = FontWeight.SemiBold); Text("You’re clear in this view. New reminders will appear automatically after sync.", color = Slate) } } }
        items(pending, key = { it.id }) { value ->
            val lead = leads.firstOrNull { it.id == value.leadId }
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(lead?.name ?: "Assigned lead", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(formatter.format(value.scheduledAt), color = Indigo, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                            value.note?.let { Text(it, color = Slate, style = MaterialTheme.typography.bodySmall) }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.complete(value.id) },
                            enabled = value.id !in completingIds,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(if (value.id in completingIds) "Saving…" else "Done", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onOpenLead(value.leadId) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Person, null, modifier = Modifier.size(15.dp))
                            Text(" Open", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { onCallLead(value.leadId) },
                            enabled = lead != null,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Call, null, modifier = Modifier.size(15.dp))
                            Text(" Call", style = MaterialTheme.typography.labelMedium)
                        }
                        if (value.type.equals("MEETING", true)) {
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) viewModel.checkIn(value)
                                    else {
                                        pendingCheckIn = value
                                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                },
                                enabled = value.id !in completingIds && value.id !in checkedInIds,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(15.dp))
                                Text(if (value.id in checkedInIds) " Done" else " Check in", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = { editing = value; editNote = value.note.orEmpty(); editAt = value.scheduledAt },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(15.dp))
                            Text(" Edit", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun MoreScreen(employeeName: String, employeePhone: String?, onProfile: () -> Unit, onReports: () -> Unit, onTeamContent: () -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    val legacyPhone = employeeName.filter(Char::isDigit).takeIf { it.length >= 10 }
    val profileName = if (legacyPhone != null && employeePhone.isNullOrBlank()) "Salesperson" else employeeName.ifBlank { "Salesperson" }
    val profilePhone = employeePhone?.ifBlank { null } ?: legacyPhone
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) ConfirmSignOutDialog(onDismiss = { confirmSignOut = false }, onConfirm = { confirmSignOut = false; onLogout() })
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
        item { SignOutButton { confirmSignOut = true } }
    }
}

@Composable private fun SignOutButton(onClick: () -> Unit) = OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(50.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .55f)),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
) { Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null); Text("  SIGN OUT") }

@Composable private fun ConfirmSignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Sign out of CallFlow?") },
    text = { Text("You’ll need to sign in again to access your leads and calls. Synced business records will remain safe.") },
    confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("SIGN OUT") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
)

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
    val syncBreakdown by viewModel.syncBreakdown.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val availability by viewModel.assignmentAvailability.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val failedRecords = syncBreakdown.filter { it.status == "FAILED" }.sumOf { it.count }
    val readiness = setupReadiness(permissions, failedRecords, conflicts)
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshPermissions() }
    val phonePermissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshPermissions() }
    var confirmEndShift by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
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
    if (confirmSignOut) ConfirmSignOutDialog(onDismiss = { confirmSignOut = false }, onConfirm = { confirmSignOut = false; viewModel.logout() })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Settings", onBack) }
        item { com.callflow.app.ui.auth.WorkspaceSupport() }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Setup health", color = Slate, style = MaterialTheme.typography.labelMedium)
            Text(readiness.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (readiness.ready) Emerald else MaterialTheme.colorScheme.error)
            Text(readiness.action, color = Slate)
            LinearProgressIndicator(progress = { setupProgress(permissions, failedRecords, conflicts) }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Text("${(setupProgress(permissions, failedRecords, conflicts) * 100).toInt()}% ready", color = if (readiness.ready) Emerald else Indigo, fontWeight = FontWeight.SemiBold)
        } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lead assignment status", fontWeight = FontWeight.SemiBold)
            Text(when { availability.loading -> "Checking your shift status…"; availability.acceptingLeads == true -> "On duty · New leads can be assigned"; availability.acceptingLeads == false -> "Off duty · New lead assignments are paused"; else -> "Status unavailable" }, fontWeight = FontWeight.Bold, color = if (availability.acceptingLeads == true) Emerald else Slate)
            Text(when (availability.acceptingLeads) { true -> "End your shift before leave or time off. Your existing leads remain available."; false -> "Resume when you are ready to receive new dashboard leads."; null -> "No assignment status change has been made." }, color = Slate, style = MaterialTheme.typography.bodySmall)
            availability.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (availability.error != null) OutlinedButton(onClick = viewModel::refreshAssignmentAvailability, enabled = !availability.loading) { Text("TRY AGAIN") }
            else if (!availability.loading && availability.acceptingLeads != null) Button(onClick = { if (availability.acceptingLeads == true) confirmEndShift = true else changeShift(true) }, enabled = !availability.saving) { Icon(Icons.Outlined.LocationOn, null); Text(if (availability.saving) "  CAPTURING LOCATION…" else if (availability.acceptingLeads == true) "  END SHIFT" else "  RESUME ASSIGNMENTS") }
            Text("Location is captured only when you start/end a shift or check in to a meeting. Continuous tracking is off.", color = Slate, style = MaterialTheme.typography.bodySmall)
        } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Sync status", fontWeight = FontWeight.SemiBold); Text(if (pending == 0) "Everything is up to date" else "$pending changes waiting to sync"); health.lastAttemptAt?.let { Text("Last attempt ${formatter.format(it)}", color = Slate) }; health.lastSuccessfulAt?.let { Text("Last successful sync ${formatter.format(it)}", color = Slate) }; health.lastError?.let { Text("Last error: $it", color = MaterialTheme.colorScheme.error) }; if (conflicts > 0) Text("$conflicts conflicts require support review", color = MaterialTheme.colorScheme.error); syncBreakdown.forEach { bucket -> Text("${bucket.entityType.reportLabel()}: ${bucket.count} ${bucket.status.reportLabel()}", color = if (bucket.status == "FAILED") MaterialTheme.colorScheme.error else Slate, style = MaterialTheme.typography.bodySmall) }; Text(if (BuildConfig.USE_FAKE_BACKEND) "Local testing environment" else "Connected to Coach For Life CRM", color = Slate); Button(onClick = viewModel::retry, enabled = !syncing) { Text(if (syncing) "SYNCING…" else if (failedRecords > 0) "RETRY FAILED SYNC" else "SYNC NOW") } } } }
        item {
            val selectedSimSlot by viewModel.selectedSimSlot.collectAsStateWithLifecycle()
            val callingAccounts by viewModel.callingAccounts.collectAsStateWithLifecycle()
            val sim1Label = callingAccounts.firstOrNull { it.slotIndex == 1 }?.label ?: "SIM 1"
            val sim2Label = callingAccounts.firstOrNull { it.slotIndex == 2 }?.label ?: "SIM 2"
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.PhoneAndroid, null, tint = Indigo, modifier = Modifier.size(20.dp))
                        Text("SIM Sync Preference (સિમ સિંક સેટિંગ્સ)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Select which SIM calls should sync to dashboard. Calls on the other SIM will be treated as personal and won't be saved or synced.", color = Slate, style = MaterialTheme.typography.bodySmall)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SimOptionRow(
                            selected = selectedSimSlot == 0,
                            title = "All SIMs (બંને સિમ)",
                            subtitle = "Sync calls from both SIM 1 & SIM 2",
                            onClick = { viewModel.selectSimSlot(0) }
                        )
                        SimOptionRow(
                            selected = selectedSimSlot == 1,
                            title = "SIM 1 Only · $sim1Label",
                            subtitle = "Only sync SIM 1 calls (Personal SIM 2 calls are ignored)",
                            onClick = {
                                val acc = callingAccounts.firstOrNull { it.slotIndex == 1 }
                                viewModel.selectSimSlot(1, acc?.id, sim1Label)
                            }
                        )
                        SimOptionRow(
                            selected = selectedSimSlot == 2,
                            title = "SIM 2 Only · $sim2Label",
                            subtitle = "Only sync SIM 2 calls (SIM 1 calls are ignored)",
                            onClick = {
                                val acc = callingAccounts.firstOrNull { it.slotIndex == 2 }
                                viewModel.selectSimSlot(2, acc?.id, sim2Label)
                            }
                        )
                    }

                    Surface(
                        color = Emerald.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Lock, null, tint = Emerald, modifier = Modifier.size(16.dp))
                            Text(
                                when (selectedSimSlot) {
                                    1 -> "Personal Privacy Active: Calls on SIM 2 will never sync to dashboard."
                                    2 -> "Personal Privacy Active: Calls on SIM 1 will never sync to dashboard."
                                    else -> "Both SIMs active: All calls from both SIMs will be synced."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Phone integration", fontWeight = FontWeight.SemiBold)
            Text("Dialer: Native mobile phone dialer (ડિફોલ્ટ કરવાની જરૂર નથી)", color = Indigo, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text("Call history sync: ${permissionLabel(permissions.callLog)}")
            Text("Notifications: ${permissionLabel(permissions.notifications)}")
            Text("CallFlow uses your mobile's own phone dialer for all calls. No need to change your default phone app. Only call data from your selected SIM is synced to the dashboard.", color = Slate, style = MaterialTheme.typography.bodySmall)
            if (permissions.callLog != PermissionState.GRANTED) Button(onClick = {
                val requested = buildList { add(Manifest.permission.CALL_PHONE); add(Manifest.permission.READ_CALL_LOG); add(Manifest.permission.READ_PHONE_STATE); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
                phonePermissionsLauncher.launch(requested.toTypedArray())
            }) { Text("ALLOW CALL PERMISSIONS") }
            else Text("Phone experience is ready", color = Emerald, fontWeight = FontWeight.Bold)
            Text("Permissions are used only to detect and sync calls from your business SIM.", color = Slate, style = MaterialTheme.typography.bodySmall)
        } } }
        item { SignOutButton { confirmSignOut = true } }
    }
}

@Composable
private fun SimOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Indigo.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (selected) Indigo else Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Indigo)
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = Slate, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Indigo, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun permissionLabel(value: PermissionState) = when (value) {
    PermissionState.GRANTED -> "Enabled"
    PermissionState.DENIED -> "Not allowed"
    PermissionState.PERMANENTLY_DENIED -> "Disabled in Android settings"
    PermissionState.NOT_REQUIRED -> "Available"
    PermissionState.ROLE_MISSING -> "Not enabled"
}

internal fun setupProgress(permissions: PermissionSummary, failed: Int, conflicts: Int): Float {
    val checks = listOf(
        permissions.callTracking != PermissionState.ROLE_MISSING,
        permissions.callLog == PermissionState.GRANTED,
        permissions.calling == PermissionState.GRANTED,
        permissions.notifications == PermissionState.GRANTED || permissions.notifications == PermissionState.NOT_REQUIRED,
        failed == 0,
        conflicts == 0,
    )
    return checks.count { it } / checks.size.toFloat()
}
