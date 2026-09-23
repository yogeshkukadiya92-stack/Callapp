package com.callflow.app.ui.operations

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.callflow.app.core.model.DispositionOption
import com.callflow.app.core.model.postCallStatuses
import com.callflow.app.ui.theme.Slate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallNoteDialog(
    name: String, phone: String, detail: String, saving: Boolean, error: String?,
    unmatched: Boolean, onDismiss: () -> Unit,
    onSave: (DispositionOption, String, Instant?) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var dateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val selected = postCallStatuses.firstOrNull { it.id == selectedId }
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault()) }
    fun chooseDate() {
        val initial = (dateMillis?.let(Instant::ofEpochMilli) ?: Instant.now().plusSeconds(3600)).atZone(ZoneId.systemDefault())
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                dateMillis = ZonedDateTime.of(year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
            }, initial.hour, initial.minute, false).show()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() }.show()
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp).imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF263246)),
            tonalElevation = 8.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFF384358), CircleShape)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Add Note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Record call disposition and follow-up plan", style = MaterialTheme.typography.bodySmall, color = Slate)
                }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, Color(0xFF263246))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (name != phone) Text(phone, style = MaterialTheme.typography.bodyMedium, color = Slate)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = Slate)
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!saving) expanded = it }) {
                    OutlinedTextField(
                        value = selected?.name.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Call Status / Disposition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        enabled = !saving,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !saving).fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        postCallStatuses.forEach { status -> DropdownMenuItem(text = { Text(status.name, fontWeight = FontWeight.Medium) }, onClick = { selectedId = status.id; expanded = false }) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = ::chooseDate,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (dateMillis != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color(0xFF2E384D))
                    ) {
                        Text(
                            dateMillis?.let { formatter.format(Instant.ofEpochMilli(it)) } ?: "Next Follow-up Date",
                            modifier = Modifier.weight(1f),
                            fontWeight = if (dateMillis != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (dateMillis != null) MaterialTheme.colorScheme.primary else Slate
                        )
                        Icon(Icons.Outlined.Event, "Choose follow-up date", tint = if (dateMillis != null) MaterialTheme.colorScheme.primary else Slate)
                    }
                    if (dateMillis != null) TextButton(onClick = { dateMillis = null }, enabled = !saving) { Text("Clear date", color = MaterialTheme.colorScheme.error) }
                    if (unmatched) Text("For unassigned numbers, the date is saved in the note. Assign a lead to schedule reminders.", style = MaterialTheme.typography.bodySmall, color = Slate)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= if (unmatched) 350 else 500) note = it },
                    label = { Text("Call Notes & Summary") },
                    placeholder = { Text("Type summary, action items, customer feedback…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 7,
                    enabled = !saving,
                    shape = RoundedCornerShape(14.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(
                        onClick = { selected?.let { onSave(it, note, dateMillis?.let(Instant::ofEpochMilli)) } },
                        enabled = selected != null && !saving,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            disabledContentColor = Color.White.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(if (saving) "Saving…" else "Save Note", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E384D))
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
