package com.callflow.app.ui.operations

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callflow.app.core.model.CallStatus
import com.callflow.app.core.model.status
import com.callflow.app.ui.theme.Emerald
import com.callflow.app.ui.theme.Indigo
import com.callflow.app.ui.theme.PremiumCard
import com.callflow.app.ui.theme.SectionHeader
import com.callflow.app.ui.theme.Slate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val callDetailsTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault())
private val quickTags = listOf("Interested", "Follow-up", "Information sent", "Busy", "Wrong number")

@Composable
fun CallDetailsScreen(
    onBack: () -> Unit,
    onOpenLead: (String) -> Unit,
    viewModel: CallDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    val call = state.call
    if (call == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (state.loading) {
                CircularProgressIndicator()
                Text("Loading call details…", modifier = Modifier.padding(top = 12.dp), color = Slate)
            } else {
                Text("Call not found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("This call may have been removed or is not available on this device.", modifier = Modifier.padding(12.dp), color = Slate)
                OutlinedButton(onClick = onBack) { Text("BACK TO CALLS") }
            }
        }
        return
    }
    fun callBack() = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.phone}"))) }
    fun openWhatsApp() = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${call.phone.filter(Char::isDigit)}"))) }
    val duration = call.answeredAt?.let { start -> call.endedAt?.epochSecond?.minus(start.epochSecond) }?.coerceAtLeast(0) ?: 0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Column { Text("Call details", style = MaterialTheme.typography.headlineMedium); Text(callDetailsTime.format(call.startedAt), color = Slate) }
            }
        }
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.lead?.name ?: "Unmatched number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(call.phone, color = Indigo, style = MaterialTheme.typography.titleMedium)
                    Text("${call.direction.name.lowercase().replaceFirstChar(Char::uppercase)} · ${call.status.label()} · ${formatDuration(duration)}", color = if (call.status == CallStatus.CONNECTED) Emerald else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    Text(call.simLabel(), color = Slate)
                    if (state.lead == null) Text("Assign this phone number to a lead in the CFL dashboard to attach notes and follow-ups.", color = Slate, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::callBack, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.Outlined.Call, null); Text("  CALL BACK") }
                OutlinedButton(onClick = ::openWhatsApp, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.AutoMirrored.Outlined.Send, null); Text("  WHATSAPP") }
            }
        }
        state.lead?.let { lead ->
            item { OutlinedButton(onClick = { onOpenLead(lead.id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Person, null); Text("  OPEN ${lead.name.uppercase()}", maxLines = 1) } }
        }
        item { SectionHeader("Call tags", "Tap to save") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickTags) { tag -> FilterChip(false, { viewModel.addNote("Call tag: $tag") }, label = { Text(tag) }, enabled = !state.saving && call.leadId != null) }
            }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                label = { Text("Call note") },
                supportingText = { Text("${note.length}/500 · Saved to the lead and CFL dashboard") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                enabled = call.leadId != null && !state.saving,
            )
        }
        item { Button(onClick = { viewModel.addNote(note) { note = "" } }, enabled = note.isNotBlank() && !state.saving && call.leadId != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (state.saving) "SAVING…" else "SAVE CALL NOTE") } }
        state.message?.let { value -> item { Text(value, color = if (value.startsWith("Saved")) Emerald else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        item { SectionHeader("Notes & tags", "${state.notes.size}") }
        if (state.notes.isEmpty()) item { PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("No notes for this call", fontWeight = FontWeight.SemiBold); Text("Add a note or tag to preserve the conversation context.", color = Slate) } } }
        items(state.notes, key = { it.id }) { item -> PremiumCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(item.detail.orEmpty(), fontWeight = FontWeight.SemiBold); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Schedule, null, tint = Slate); Text("  ${callDetailsTime.format(item.occurredAt)}", color = Slate, style = MaterialTheme.typography.bodySmall) } } } }
    }
}

private fun CallStatus.label() = when (this) { CallStatus.CONNECTED -> "Connected"; CallStatus.MISSED -> "Missed"; CallStatus.NOT_CONNECTED -> "Not connected" }
private fun com.callflow.app.core.model.CallRecord.simLabel() = when {
    simSlot != null && !simLabel.isNullOrBlank() -> "SIM $simSlot · $simLabel"
    simSlot != null -> "SIM $simSlot"
    !simLabel.isNullOrBlank() -> simLabel
    else -> "SIM information unavailable"
}
