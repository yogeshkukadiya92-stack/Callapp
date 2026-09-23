package com.callflow.app.ui.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class CallNotePresetsTest {
    @Test fun selectingAndRemovingPreservesWrittenNotes() {
        val initial = "Discuss pricing next week."
        val selected = toggleCallNotePreset(initial, "Customer is interested.")
        assertEquals("$initial\nCustomer is interested.", selected)
        assertEquals(initial, toggleCallNotePreset(selected, "Customer is interested."))
    }
    @Test fun editedSnippetIsNotDeletedBySelectingPreset() {
        assertEquals("Wrong number. Correct number pending.\nWrong number.", toggleCallNotePreset("Wrong number. Correct number pending.", "Wrong number."))
    }
    @Test fun presetCannotExceedNoteLimit() {
        val initial = "x".repeat(495)
        assertEquals(initial, toggleCallNotePreset(initial, "Wrong number."))
    }
}
