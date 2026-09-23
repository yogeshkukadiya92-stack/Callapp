package com.callflow.app.ui.calling

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.telecom.CallIntegrationState
import com.callflow.app.telecom.CallingAccount
import com.callflow.app.core.model.PermissionState
import com.callflow.app.ui.theme.Emerald
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Switch

private val followUpFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM · hh:mm a").withZone(ZoneId.systemDefault())

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ManualDialScreen(
    onBack: () -> Unit,
    onOpenLeadCall: (String) -> Unit,
    viewModel: ManualDialViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingCall by remember { mutableStateOf(false) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingCall) viewModel.callUnknown()
        pendingCall = false
    }
    fun callUnknown() {
        if (state.integrationState == CallIntegrationState.Ready && !viewModel.hasDirectCallPermission()) {
            pendingCall = true
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } else viewModel.callUnknown()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Dial number", style = MaterialTheme.typography.titleLarge)
        }
        Text("Call any customer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Assigned numbers automatically use the complete tracked lead flow.", color = Slate)
        OutlinedTextField(
            value = state.number,
            onValueChange = viewModel::updateNumber,
            label = { Text("Phone number") },
            placeholder = { Text("Enter mobile number") },
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("+", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .combinedClickable(
                                onClick = { if (key == "⌫") viewModel.backspace() else viewModel.append(key) },
                                onLongClick = { if (key == "⌫") viewModel.clearNumber() },
                                onLongClickLabel = if (key == "⌫") "Clear number" else null,
                            ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(key, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        state.matchedLead?.let { lead ->
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ASSIGNED LEAD FOUND", color = Indigo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(lead.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    lead.company?.let { Text(it, color = Slate) }
                    Text(lead.displayPhone)
                    if (lead.doNotCall) Text("Do Not Call — calling is blocked from the dashboard.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { onOpenLeadCall(lead.id) },
                        enabled = !lead.doNotCall,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Icon(Icons.Outlined.Call, null); Text(if (lead.doNotCall) "  CALL BLOCKED" else "  CALL ASSIGNED LEAD") }
                }
            }
        }
        state.matchedContactName?.let { contactName ->
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SAVED CONTACT FOUND", color = Emerald, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(contactName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(state.number, color = Slate)
                    Button(
                        onClick = ::callUnknown,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Icon(Icons.Outlined.Call, null); Text("  CALL $contactName") }
                }
            }
        }
        if (state.number.count(Char::isDigit) >= 7 && state.matchedLead == null && state.matchedContactName == null) {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("UNSAVED NUMBER", color = Slate, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("This number is not in your assigned leads or phone contacts. The call will be tracked by phone number directly.")
            } }
        }
        if (state.matchedLead == null && state.matchedContactName == null) SimAccountSelector(state.callingAccounts, state.selectedAccountId, viewModel::selectAccount)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        state.message?.let { Text(it, color = Indigo) }
        if (state.matchedLead == null && state.matchedContactName == null) Button(
            onClick = ::callUnknown,
            enabled = state.number.count(Char::isDigit) >= 7,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Icon(Icons.Outlined.Call, null); Text("  CALL NOW") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun CallingScreen(onBack: () -> Unit, onCallStarted: (String, String) -> Unit, autoStart: Boolean = false, viewModel: CallingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRole() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.roleIntent()?.let(roleLauncher::launch) }
    val callLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshCallLogPermission() }
    val lead = state.lead
    var confirmDuplicate by remember(lead?.id) { mutableStateOf(false) }
    var pendingDirectCall by remember { mutableStateOf(false) }
    var autoStartHandled by remember { mutableStateOf(false) }
    var completedCallHandled by remember(lead?.id) { mutableStateOf(false) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingDirectCall) lead?.let { viewModel.call { } }
        pendingDirectCall = false
    }
    fun startCall() {
        if (state.integrationState == CallIntegrationState.Ready && !viewModel.hasDirectCallPermission()) {
            pendingDirectCall = true
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } else viewModel.call { }
    }
    LaunchedEffect(autoStart, lead?.id) {
        if (autoStart && lead != null && !autoStartHandled) {
            autoStartHandled = true
            if (!lead.doNotCall) {
                if (lead.duplicateCount > 1) confirmDuplicate = true else startCall()
            }
        }
    }
    LaunchedEffect(state.activeCall?.endedAt, state.callId) {
        val completed = state.activeCall
        if (!completedCallHandled && completed?.endedAt != null && state.callId != null && lead != null) {
            completedCallHandled = true
            onCallStarted(lead.id, checkNotNull(state.callId))
        }
    }
    if (confirmDuplicate && lead != null) {
        AlertDialog(
            onDismissRequest = { confirmDuplicate = false },
            title = { Text("Possible duplicate lead") },
            text = { Text("This phone number appears on ${lead.duplicateCount} assigned lead records. Check the customer details before continuing.") },
            confirmButton = { Button(onClick = { confirmDuplicate = false; startCall() }) { Text("CONTINUE") } },
            dismissButton = { OutlinedButton(onClick = { confirmDuplicate = false }) { Text("CANCEL") } },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }; Text("Call lead", style = MaterialTheme.typography.titleLarge) }
        Text("Ready to call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (lead != null) { Text(lead.name, style = MaterialTheme.typography.titleLarge); lead.company?.let { Text(it) }; Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary) }
        SimAccountSelector(state.callingAccounts, state.selectedAccountId, viewModel::selectAccount)
        if (lead?.doNotCall == true) Card { Column(Modifier.padding(14.dp)) { Text("Do Not Call", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); Text("Calling is disabled because this number is blocked on the dashboard.") } }
        if ((lead?.duplicateCount ?: 1) > 1) Card { Column(Modifier.padding(14.dp)) { Text("Possible duplicate", fontWeight = FontWeight.Bold); Text("${lead?.duplicateCount} lead records use this phone number. Confirmation is required before calling.") } }
        if (state.callLogPermission != PermissionState.GRANTED) {
            Card { Column(Modifier.padding(14.dp)) { Text("Call log sync is off", fontWeight = FontWeight.SemiBold); Text("Allow call log and phone-state access so all completed calls, exact duration, and SIM details can sync automatically."); Button(onClick = { callLogLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)) }) { Text("ALLOW CALL LOG SYNC") } } }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if(state.subscriptionReadOnly) Card { Column(Modifier.padding(14.dp)) { Text("Subscription expired",color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold);Text("Your synced data remains available. Renew the company subscription to resume calling and syncing.") } }
        Button(onClick = { if ((lead?.duplicateCount ?: 1) > 1) confirmDuplicate = true else startCall() }, enabled = lead != null && lead.doNotCall.not() && !state.subscriptionReadOnly, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Outlined.Call, null); Text(if(state.subscriptionReadOnly) "  RENEW TO CALL" else if (lead?.doNotCall == true) "  CALL BLOCKED" else "  CALL NOW") }
        Text("Your mobile's native dialer will place this call. Completed calls, duration and SIM will sync automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SimAccountSelector(accounts: List<CallingAccount>, selectedId: String?, onSelect: (String?) -> Unit) {
    if (accounts.size <= 1) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Call using", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selectedId == null, { onSelect(null) }, label = { Text("System default") })
            accounts.forEachIndexed { index, account -> FilterChip(selectedId == account.id, { onSelect(account.id) }, label = { Text(account.label.ifBlank { "SIM ${index + 1}" }) }) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DispositionScreen(onBack: () -> Unit, onSaved: () -> Unit, onSaveNext: (String?) -> Unit, viewModel: DispositionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun requireResult() { android.widget.Toast.makeText(context, "Save the call status and required follow-up before leaving.", android.widget.Toast.LENGTH_SHORT).show() }
    androidx.activity.compose.BackHandler { requireResult() }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::addSuggestion)
        }
    }
    fun startVoiceNote() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the call note")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { android.widget.Toast.makeText(context, "Voice typing is not available on this phone.", android.widget.Toast.LENGTH_LONG).show() }
    }
    fun chooseCustomDateTime() {
        val initial = (state.followUpAt ?: java.time.Instant.now().plusSeconds(3600)).atZone(ZoneId.systemDefault())
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                viewModel.scheduleAt(ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant())
            }, initial.hour, initial.minute, false).show()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() }.show()
    }
    fun saveAndWhatsApp() {
        viewModel.save {
            openCalendarReminder(context, state)
            val phone = state.lead?.normalizedPhone?.filter(Char::isDigit).orEmpty()
            val message = state.note.ifBlank { "Thank you for speaking with us." }
            runCatching { com.callflow.app.core.openWhatsApp(context, phone, message) }
                .onFailure { android.widget.Toast.makeText(context, "Saved. Unable to open WhatsApp; check that WhatsApp or WhatsApp Business is installed.", android.widget.Toast.LENGTH_LONG).show() }
            onSaved()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Call result · Required", style = MaterialTheme.typography.titleLarge); Text("How was the call with ${state.lead?.name ?: "this lead"}?", style = MaterialTheme.typography.headlineMedium); Text("Save the status and required follow-up to continue.", color = Slate) }
        item { SectionHeader("Disposition") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.options.forEach { option -> FilterChip(selected = state.selected?.id == option.id, onClick = { viewModel.select(option) }, label = { Text(option.name) }) } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Call notes", style = MaterialTheme.typography.titleMedium); IconButton(onClick = ::startVoiceNote) { Icon(Icons.Outlined.Mic, "Speak call note") } }
            OutlinedTextField(value = state.note, onValueChange = viewModel::note, placeholder = { Text("Add details from the conversation…") }, supportingText = { Text(if (state.selected?.requiresNote == true) "A note is required for this result" else "Optional · ${state.note.length}/500") }, minLines = 4, maxLines = 8, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Text("Quick templates", color = Slate, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { state.noteTemplates.forEach { suggestion -> AssistChip(onClick = { viewModel.addSuggestion(suggestion) }, label = { Text(suggestion) }) } }
        } } }
        if (state.selected?.code in setOf("GENERATE_MEETING", "MEETING_BOOKED", "MEETING_NO_SHOW", "ONLINE_INTRO")) item {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meeting details", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(state.meetingLink, viewModel::meetingLink, label = { Text("Google Meet or Zoom link (optional)") }, placeholder = { Text("https://meet.google.com/…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Event, null); Text("  Add calendar reminder") }; Switch(state.addToCalendar, viewModel::addToCalendar) }
            } }
        }
        item { SectionHeader(when (state.selected?.code) { "GENERATE_MEETING" -> "Meeting date & time"; "ONLINE_INTRO" -> "Online intro date & time"; "NEXT_TIME_ATTEND" -> "Next intro date & time"; else -> "Quick follow-up" }) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { viewModel.schedule(3_600) }, label = { Text("In 1 hour") }); AssistChip(onClick = { viewModel.schedule(86_400) }, label = { Text("Tomorrow") }); AssistChip(onClick = viewModel::scheduleNextMonday, label = { Text("Next Monday") }); AssistChip(onClick = ::chooseCustomDateTime, label = { Text("CUSTOM DATE & TIME") }) }; state.followUpAt?.let { Text("Reminder set for ${followUpFormatter.format(it)}", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        item { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { viewModel.save { openCalendarReminder(context, state); onSaved() } }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE") }; Button(onClick = { viewModel.saveNext { next -> openCalendarReminder(context, state); onSaveNext(next) } }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE & NEXT") } }; Button(onClick = ::saveAndWhatsApp, enabled = !state.saving && state.lead != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("SAVE & GO TO WHATSAPP") } } }
    }
}

private fun openCalendarReminder(context: android.content.Context, state: DispositionUiState) {
    val at = state.followUpAt ?: return
    if (!state.addToCalendar || state.selected?.code !in setOf("GENERATE_MEETING", "MEETING_BOOKED", "ONLINE_INTRO")) return
    val start = at.toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000)
        putExtra(CalendarContract.Events.TITLE, "CallFlow meeting · ${state.lead?.name ?: "Lead"}")
        putExtra(CalendarContract.Events.DESCRIPTION, listOf(state.note, state.meetingLink).filter(String::isNotBlank).joinToString("\n"))
    }
    runCatching { context.startActivity(intent) }
        .onFailure { android.widget.Toast.makeText(context, "Saved. No calendar app is available.", android.widget.Toast.LENGTH_LONG).show() }
}
