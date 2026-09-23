package com.callflow.app.ui.operations

internal data class CallNotePreset(val label: String, val body: String)

internal val callNotePresets = listOf(
    CallNotePreset("Received", "Call received."),
    CallNotePreset("No answer", "Call not answered."),
    CallNotePreset("Busy", "The number was busy."),
    CallNotePreset("Connected", "Call connected; spoke with the customer."),
    CallNotePreset("Not connected", "Call could not connect."),
    CallNotePreset("Switched off", "The phone was switched off."),
    CallNotePreset("Unreachable", "The number was unreachable."),
    CallNotePreset("Wrong number", "Wrong number."),
    CallNotePreset("Interested", "Customer is interested."),
    CallNotePreset("Not interested", "Customer is not interested."),
    CallNotePreset("Call back", "Customer requested a call back."),
    CallNotePreset("Details sent", "Shared the requested details."),
)

/** Keep free text intact and prevent duplicate snippets or notes above the storage limit. */
internal fun toggleCallNotePreset(note: String, body: String): String {
    if (note.lines().any { it == body }) return note.lines().filterNot { it == body }.joinToString("\n")
    val updated = if (note.isEmpty()) body else note + (if (note.endsWith("\n")) "" else "\n") + body
    return updated.takeIf { it.length <= 500 } ?: note
}
