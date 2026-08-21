package com.callflow.app.ui.calling

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.telecom.CallIntegrationState
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate

@Composable
fun CallingScreen(onCallStarted: (String, String) -> Unit, viewModel: CallingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshRole() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.roleIntent()?.let(roleLauncher::launch) }
    val lead = state.lead
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ready to call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (lead != null) { Text(lead.name, style = MaterialTheme.typography.titleLarge); lead.company?.let { Text(it) }; Text(lead.displayPhone, color = MaterialTheme.colorScheme.primary) }
        if (state.integrationState == CallIntegrationState.RoleRequired) {
            Card { Column(Modifier.padding(14.dp)) { Text("Automatic call tracking is off", fontWeight = FontWeight.SemiBold); Text("The Phone role enables incoming-call controls and lifecycle tracking. Notification access shows calls reliably while the app is closed. You can continue manually if you decline."); Button(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.roleIntent()?.let(roleLauncher::launch) }) { Text("ENABLE CALL TRACKING") } } }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        Button(onClick = { viewModel.call { callId -> lead?.let { onCallStarted(it.id, callId) } } }, enabled = lead != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Outlined.Call, null); Text("  CALL NOW") }
        Text("The attempt is saved before the phone app opens. Return here after the call to record the result.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DispositionScreen(onSaved: () -> Unit, onSaveNext: () -> Unit, viewModel: DispositionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("How was the call with ${state.lead?.name ?: "this lead"}?", style = MaterialTheme.typography.headlineMedium); Text("Log the outcome to keep your pipeline updated.", color = Slate) }
        item { SectionHeader("Disposition") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.options.forEach { option -> FilterChip(selected = state.selected?.id == option.id, onClick = { viewModel.select(option) }, label = { Text(option.name) }) } } }
        item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Call notes", style = MaterialTheme.typography.titleMedium); Row { Icon(Icons.Outlined.Mic, null, tint = Indigo); Text(" Voice note", color = Indigo, style = MaterialTheme.typography.labelLarge) } }
            OutlinedTextField(value = state.note, onValueChange = viewModel::note, placeholder = { Text("Add details from the conversation…") }, supportingText = { Text(if (state.selected?.requiresNote == true) "A note is required for this result" else "Optional · ${state.note.length}/500") }, minLines = 4, maxLines = 8, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Scheduled demo", "Pricing discussed", "Follow-up next week").forEach { suggestion -> AssistChip(onClick = { viewModel.addSuggestion(suggestion) }, label = { Text(suggestion) }) } }
        } } }
        item { SectionHeader("Quick follow-up") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { viewModel.schedule(3_600) }, label = { Text("In 1 hour") }); AssistChip(onClick = { viewModel.schedule(86_400) }, label = { Text("Tomorrow") }); AssistChip(onClick = { viewModel.schedule(604_800) }, label = { Text("Next Monday") }) }; state.followUpAt?.let { Text("Scheduled: $it", color = Indigo) } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { viewModel.save(onSaved) }, enabled = !state.saving, modifier = Modifier.weight(1f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text("SAVE") }; Button(onClick = { viewModel.save(onSaveNext) }, enabled = !state.saving, modifier = Modifier.weight(1.35f).height(56.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Text("SAVE & NEXT") } } }
    }
}
