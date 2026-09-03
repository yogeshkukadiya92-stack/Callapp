package com.callflow.app.ui.calling

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext

private val followUpFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM · hh:mm a").withZone(ZoneId.systemDefault())

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
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("+", "0", "⌫")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { if (key == "⌫") viewModel.backspace() else viewModel.append(key) },
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) { Text(key, style = MaterialTheme.typography.titleLarge) }
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
        if (state.number.count(Char::isDigit) >= 7 && state.matchedLead == null) {
            PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("UNASSIGNED NUMBER", color = Slate, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("This number is not in your assigned leads. If you make the call, the actual call-log entry will be saved as Unknown / Unassigned.")
            } }
        }
        if (state.matchedLead == null) SimAccountSelector(state.callingAccounts, state.selectedAccountId, viewModel::selectAccount)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        state.message?.let { Text(it, color = Indigo) }
        if (state.matchedLead == null) Button(
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
        if (state.integrationState == CallIntegrationState.RoleRequired) {
            Card { Column(Modifier.padding(14.dp)) { Text("Automatic call tracking is off", fontWeight = FontWeight.SemiBold); Text("The Phone role enables incoming-call controls and lifecycle tracking. Notification access shows calls reliably while the app is closed. You can continue manually if you decline."); Button(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.roleIntent()?.let(roleLauncher::launch) }) { Text("ENABLE CALL TRACKING") } } }
        }
        if (state.callLogPermission != PermissionState.GRANTED) {
            Card { Column(Modifier.padding(14.dp)) { Text("Call log sync is off", fontWeight = FontWeight.SemiBold); Text("Allow call log and phone-state access so all completed calls, exact duration, and SIM details can sync automatically."); Button(onClick = { callLogLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)) }) { Text("ALLOW CALL LOG SYNC") } } }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        Button(onClick = { if ((lead?.duplicateCount ?: 1) > 1) confirmDuplicate = true else startCall() }, enabled = lead != null && lead.doNotCall.not(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Outlined.Call, null); Text(if (lead?.doNotCall == true) "  CALL BLOCKED" else "  CALL NOW") }
        Text(if (state.integrationState == CallIntegrationState.Ready) "The CallFlow phone screen opens now. The result form appears only after the call actually ends." else "Manual dialer mode: history is created only after an actual call appears in Android call log.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val phone = state.lead?.normalizedPhone?.filter(Char::isDigit).orEmpty()
            val message = state.note.ifBlank { "Thank you for speaking with us." }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}"))) }
            onSaved()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }; Text("Call result", style = MaterialTheme.typography.titleLarge) }; Text("How was the call with ${state.lead?.name ?: "this lead"}?", style = MaterialTheme.typography.headlineMedium); Text("Log the outcome to keep your pipeline updated.", color = Slate) }
        item { SectionHeader("Disposition") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.options.forEach { option -> FilterChip(selected = state.selected?.id == option.id, onClick = { viewModel.select(option) }, label = { Text(option.name) }) } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Call notes", style = MaterialTheme.typography.titleMedium); Text("Synced securely", color = Slate, style = MaterialTheme.typography.labelMedium) }
            OutlinedTextField(value = state.note, onValueChange = viewModel::note, placeholder = { Text("Add details from the conversation…") }, supportingText = { Text(if (state.selected?.requiresNote == true) "A note is required for this result" else "Optional · ${state.note.length}/500") }, minLines = 4, maxLines = 8, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Scheduled demo", "Pricing discussed", "Follow-up next week").forEach { suggestion -> AssistChip(onClick = { viewModel.addSuggestion(suggestion) }, label = { Text(suggestion) }) } }
        } } }
        item { SectionHeader(when (state.selected?.code) { "GENERATE_MEETING" -> "Meeting date & time"; "ONLINE_INTRO" -> "Online intro date & time"; "NEXT_TIME_ATTEND" -> "Next intro date & time"; else -> "Quick follow-up" }) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { viewModel.schedule(3_600) }, label = { Text("In 1 hour") }); AssistChip(onClick = { viewModel.schedule(86_400) }, label = { Text("Tomorrow") }); AssistChip(onClick = viewModel::scheduleNextMonday, label = { Text("Next Monday") }); AssistChip(onClick = ::chooseCustomDateTime, label = { Text("CUSTOM DATE & TIME") }) }; state.followUpAt?.let { Text("Reminder set for ${followUpFormatter.format(it)}", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        item { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { viewModel.save(onSaved) }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE") }; Button(onClick = { viewModel.saveNext(onSaveNext) }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE & NEXT") } }; Button(onClick = ::saveAndWhatsApp, enabled = !state.saving && state.lead != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("SAVE & GO TO WHATSAPP") } } }
    }
}
