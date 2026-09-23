package com.callflow.app.telecom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostCallCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("pending-call-results", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun queuedCallsSurviveRecreationUntilSaved() {
        val first = PostCallCoordinator(context)
        first.show("lead1", "call1")
        first.show("lead2", "call2")
        first.show("lead1", "call1")
        first.consume(first.target.value!!)
        val restored = PostCallCoordinator(context)
        assertEquals("call2", restored.target.value?.callId)
        restored.complete("call2")
        assertEquals("call1", restored.target.value?.callId)
        restored.complete("call1")
        assertNull(restored.target.value)
    }
    @Test fun deferredNoteDoesNotHideNewCallAndDuplicateDoesNotReopenOldCall() {
        val coordinator = PostCallCoordinator(context)
        coordinator.show(null, "older")
        coordinator.consume(coordinator.target.value!!)
        coordinator.show("lead", "newer")
        assertEquals("newer", coordinator.target.value?.callId)
        coordinator.show(null, "older")
        assertEquals("newer", coordinator.target.value?.callId)
        coordinator.complete("newer")
        assertEquals("older", coordinator.target.value?.callId)
    }
    @Test fun unmatchedCallSurvivesRecreation() {
        PostCallCoordinator(context).show(null, "unmatched-call")
        val restored = PostCallCoordinator(context)
        assertEquals("unmatched-call", restored.target.value?.callId)
        assertNull(restored.target.value?.leadId)
    }
}
