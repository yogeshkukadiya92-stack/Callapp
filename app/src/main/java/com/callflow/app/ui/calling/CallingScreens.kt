package com.callflow.app.ui.calling

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(state.lead?.name ?: "Post call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("CALL RESULT", style = MaterialTheme.typography.labelLarge) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { state.options.forEach { option -> FilterChip(selected = state.selected?.id == option.id, onClick = { viewModel.select(option) }, label = { Text(option.name) }) } } }
        item { OutlinedTextField(state.note, viewModel::note, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
        item { Text("Follow-up", fontWeight = FontWeight.SemiBold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { viewModel.schedule(86_400) }, label = { Text("Tomorrow") }); AssistChip(onClick = { viewModel.schedule(172_800) }, label = { Text("2 Days") }); AssistChip(onClick = { viewModel.schedule(604_800) }, label = { Text("Next Week") }) }; state.followUpAt?.let { Text("Scheduled: $it") } }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) } }
        item { Button(onClick = { viewModel.save(onSaved) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("SAVE") } }
        item { Button(onClick = { viewModel.save(onSaveNext) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("SAVE & NEXT") } }
    }
}
