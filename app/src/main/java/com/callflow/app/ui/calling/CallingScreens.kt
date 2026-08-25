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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.telecom.CallIntegrationState
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
fun CallingScreen(onBack: () -> Unit, onCallStarted: (String, String) -> Unit, viewModel: CallingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRole() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.roleIntent()?.let(roleLauncher::launch) }
    val callLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshCallLogPermission() }
    val lead = state.lead
    var confirmDuplicate by remember(lead?.id) { mutableStateOf(false) }
    var pendingDirectCall by remember { mutableStateOf(false) }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingDirectCall) lead?.let { viewModel.call { callId -> onCallStarted(it.id, callId) } }
        pendingDirectCall = false
    }
    fun startCall() {
        if (state.integrationState == CallIntegrationState.Ready && !viewModel.hasDirectCallPermission()) {
            pendingDirectCall = true
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } else viewModel.call { callId -> lead?.let { onCallStarted(it.id, callId) } }
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
        if (lead?.doNotCall == true) Card { Column(Modifier.padding(14.dp)) { Text("Do Not Call", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); Text("Calling is disabled because this number is blocked on the dashboard.") } }
        if ((lead?.duplicateCount ?: 1) > 1) Card { Column(Modifier.padding(14.dp)) { Text("Possible duplicate", fontWeight = FontWeight.Bold); Text("${lead?.duplicateCount} lead records use this phone number. Confirmation is required before calling.") } }
        if (state.integrationState == CallIntegrationState.RoleRequired) {
            Card { Column(Modifier.padding(14.dp)) { Text("Automatic call tracking is off", fontWeight = FontWeight.SemiBold); Text("The Phone role enables incoming-call controls and lifecycle tracking. Notification access shows calls reliably while the app is closed. You can continue manually if you decline."); Button(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.roleIntent()?.let(roleLauncher::launch) }) { Text("ENABLE CALL TRACKING") } } }
        }
        if (state.callLogPermission != PermissionState.GRANTED) {
            Card { Column(Modifier.padding(14.dp)) { Text("Call log sync is off", fontWeight = FontWeight.SemiBold); Text("Allow call log access so completed phone calls with assigned leads can sync to the dashboard automatically."); Button(onClick = { callLogLauncher.launch(Manifest.permission.READ_CALL_LOG) }) { Text("ALLOW CALL LOG SYNC") } } }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        Button(onClick = { if ((lead?.duplicateCount ?: 1) > 1) confirmDuplicate = true else startCall() }, enabled = lead != null && lead.doNotCall.not(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Outlined.Call, null); Text(if (lead?.doNotCall == true) "  CALL BLOCKED" else "  CALL NOW") }
        Text(if (state.integrationState == CallIntegrationState.Ready) "Starts the call directly. The result screen is ready when the call ends." else "Manual dialer mode: history is created only after an actual call appears in Android call log.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DispositionScreen(onBack: () -> Unit, onSaved: () -> Unit, onSaveNext: () -> Unit, viewModel: DispositionViewModel = hiltViewModel()) {
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
        item { SectionHeader("Quick follow-up") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { viewModel.schedule(3_600) }, label = { Text("In 1 hour") }); AssistChip(onClick = { viewModel.schedule(86_400) }, label = { Text("Tomorrow") }); AssistChip(onClick = viewModel::scheduleNextMonday, label = { Text("Next Monday") }); AssistChip(onClick = ::chooseCustomDateTime, label = { Text("CUSTOM DATE & TIME") }) }; state.followUpAt?.let { Text("Reminder set for ${followUpFormatter.format(it)}", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        item { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { viewModel.save(onSaved) }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE") }; Button(onClick = { viewModel.save(onSaveNext) }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (state.saving) "SAVING…" else "SAVE & NEXT") } }; Button(onClick = ::saveAndWhatsApp, enabled = !state.saving && state.lead != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("SAVE & GO TO WHATSAPP") } } }
    }
}
