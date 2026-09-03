package com.callflow.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callflow.app.core.model.CallDirection
import com.callflow.app.core.model.Lead
import com.callflow.app.core.model.SyncStatus
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.data.local.CallEntity
import com.callflow.app.data.local.CallEventEntity
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.local.SyncEventEntity
import com.callflow.app.data.remote.DeltaSyncResponse
import com.callflow.app.data.remote.LeadDeltaDto
import com.callflow.app.data.session.SyncCursorStore
import com.callflow.app.sync.DeltaSyncApplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineCallDurabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "offline-call-durability-test.db"
    private val now = Instant.parse("2026-08-19T09:30:00Z")
    private val clock = object : DateTimeProvider { override fun now(): Instant = now }
    private lateinit var database: CallFlowDatabase

    @Before fun setUp() {
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun call_attempt_survives_database_reopen_with_lifecycle_and_outbox() = runTest {
        val repository = OfflineCallRepository(database, database.dao(), clock)
        val lead = Lead(
            id = "lead-1",
            serverId = null,
            name = "Anita",
            company = "Acme",
            city = "Mumbai",
            normalizedPhone = "+919876543210",
            displayPhone = "+91 98765 43210",
            stageId = "new",
            assignedUserId = "employee",
            campaignId = "campaign-1",
            nextFollowUpAt = null,
            updatedAt = now,
            version = 1,
        )

        val callId = repository.startOutgoingCall(lead).getOrThrow()
        database.close()
        database = openDatabase()

        val restoredCall = database.dao().observeCall(callId).first()
        assertNotNull(restoredCall)
        assertEquals(now.toEpochMilli(), restoredCall?.startedAt)
        assertEquals(listOf("INITIATED"), database.dao().callEvents(callId).map { it.type })
        val outbox = database.dao().syncEvents("CALL", callId)
        assertEquals(1, outbox.size)
        assertEquals(SyncStatus.PENDING.name, outbox.single().status)
    }

    @Test fun duplicate_outbox_event_rolls_back_the_entire_call_attempt() = runTest {
        val dao = database.dao()
        val duplicate = SyncEventEntity("event-1", "stable-event", "CALL", "existing", "CREATE", "{}", now.toEpochMilli(), 0, null, SyncStatus.PENDING.name, null)
        dao.insertSyncEvent(duplicate)
        val call = CallEntity("call-rollback", null, null, "employee", null, "+919999999999", CallDirection.OUTGOING.name, now.toEpochMilli(), null, null, null, SyncStatus.PENDING.name)
        val lifecycle = CallEventEntity("lifecycle-rollback", call.id, "INITIATED", now.toEpochMilli())
        val conflicting = duplicate.copy(id = "event-2", entityId = call.id)

        assertTrue(runCatching { dao.insertCallAttemptWithOutbox(call, lifecycle, conflicting) }.isFailure)
        assertEquals(null, dao.observeCall(call.id).first())
        assertTrue(dao.callEvents(call.id).isEmpty())
    }

    @Test fun a_synced_outbox_event_is_never_regressed_when_a_later_sync_step_fails() = runTest {
        val dao = database.dao()
        val event = SyncEventEntity("event-synced", "stable-synced-event", "CALL", "call-1", "CREATE", "{}", now.toEpochMilli(), 0, null, SyncStatus.PENDING.name, null)
        dao.insertSyncEvent(event)

        dao.markSynced(listOf(event.eventUuid))
        dao.markSyncFailed(listOf(event.eventUuid), "Delta download failed")

        val saved = dao.syncEvents("CALL", "call-1").single()
        assertEquals(SyncStatus.SYNCED.name, saved.status)
        assertEquals(null, saved.lastError)
    }

    @Test fun phase_one_dispositions_include_callback_and_custom_outcome() = runTest {
        val repository = OfflineCallRepository(database, database.dao(), clock)
        repository.seedDispositionsIfEmpty()
        val options = repository.observeDispositions().first()
        assertTrue(options.any { it.code == "CALLBACK_REQUESTED" && it.requiresFollowUp })
        assertTrue(options.any { it.code == "WARM" })
        assertTrue(options.any { it.code == "INVITE_INTRO" })
        assertTrue(options.any { it.code == "ONLINE_INTRO" && it.requiresFollowUp })
        assertTrue(options.any { it.code == "NEXT_TIME_ATTEND" && it.requiresFollowUp })
        assertTrue(options.any { it.code == "INTRO_ATTENDED" })
        assertTrue(options.any { it.code == "NOT_ELIGIBLE" && it.requiresNote })
        assertTrue(options.any { it.code == "GENERATE_MEETING" && it.requiresFollowUp })
        assertTrue(options.any { it.code == "CUSTOM" && it.requiresNote })
    }

    @Test fun call_note_is_saved_with_dashboard_outbox_and_validated() = runTest {
        val repository = OfflineCallRepository(database, database.dao(), clock)

        repository.addCallNote("call-1", "lead-1", "  Customer requested brochure  ").getOrThrow()

        val notes = database.dao().observeCallNotes("call-1").first()
        assertEquals(1, notes.size)
        assertEquals("Customer requested brochure", notes.single().body)
        val events = database.dao().syncEvents("NOTE", notes.single().id)
        assertEquals(1, events.size)
        assertTrue(events.single().payload.contains("Customer requested brochure"))
        assertTrue(repository.addCallNote("call-1", "lead-1", "   ").isFailure)
        assertTrue(repository.addCallNote("call-1", "lead-1", "x".repeat(501)).isFailure)
    }

    @Test fun system_call_log_reconciles_platform_call_without_creating_a_duplicate() = runTest {
        val dao = database.dao()
        val startedAt = now.toEpochMilli()
        val original = CallEntity("platform-call", null, "lead-1", "employee", "campaign-1", "+919876543210", CallDirection.OUTGOING.name, startedAt + 2_000, null, null, null, SyncStatus.PENDING.name)
        dao.insertCall(original)

        val match = dao.findMatchingPlatformCall(original.normalizedPhone, original.direction, startedAt, startedAt - 120_000, startedAt + 120_000)
        assertEquals(original.id, match?.id)
        val sync = SyncEventEntity("reconcile-event", "reconcile-event", "CALL", original.id, "CREATE", "{\"durationSeconds\":125}", startedAt + 127_000, 0, null, SyncStatus.PENDING.name, null)
        dao.reconcileImportedCallWithOutbox(original.id, "lead-1", "campaign-1", startedAt, startedAt, startedAt + 125_000, null, 2, "Work SIM", "account-2", sync)

        val reconciled = dao.observeRecentCalls().first()
        assertEquals(1, reconciled.size)
        assertEquals(startedAt + 125_000, reconciled.single().endedAt)
        assertEquals(2, reconciled.single().simSlot)
        assertEquals("Work SIM", reconciled.single().simLabel)
        assertEquals(1, dao.syncEvents("CALL", original.id).size)
    }

    @Test fun lead_call_summary_separates_connected_missed_and_not_connected_with_talk_time() = runTest {
        val dao = database.dao()
        val startedAt = now.toEpochMilli()
        dao.insertCall(CallEntity("connected", null, "lead-1", "employee", null, "+919876543210", CallDirection.OUTGOING.name, startedAt, startedAt + 5_000, startedAt + 130_000, null, SyncStatus.SYNCED.name))
        dao.insertCall(CallEntity("missed", null, "lead-1", "employee", null, "+919876543210", CallDirection.INCOMING.name, startedAt + 200_000, null, startedAt + 220_000, null, SyncStatus.SYNCED.name))
        dao.insertCall(CallEntity("not-connected", null, "lead-1", "employee", null, "+919876543210", CallDirection.OUTGOING.name, startedAt + 300_000, null, startedAt + 315_000, null, SyncStatus.SYNCED.name))

        val summary = dao.observeLeadCallSummaries().first().single()
        assertEquals(3, summary.attempts)
        assertEquals(1, summary.connected)
        assertEquals(1, summary.missed)
        assertEquals(1, summary.notConnected)
        assertEquals(125L, summary.talkTimeSeconds)
        assertEquals(startedAt, summary.firstContactedAt)
        assertEquals(startedAt + 300_000, summary.lastContactedAt)
    }

    @Test fun dashboard_lead_assignment_links_older_unmatched_calls_and_queues_audit_update() = runTest {
        val dao = database.dao()
        dao.insertCall(CallEntity("unmatched-call", null, null, "employee", null, "+919876543210", CallDirection.OUTGOING.name, now.toEpochMilli(), now.toEpochMilli(), now.plusSeconds(60).toEpochMilli(), null, SyncStatus.SYNCED.name))
        val cursorStore = SyncCursorStore(context)
        cursorStore.clear()
        val applier = DeltaSyncApplier(database, dao, cursorStore, clock)
        applier.apply(
            DeltaSyncResponse(
                leads = listOf(LeadDeltaDto("lead-live", "server-lead", "Assigned Lead", null, null, "+919876543210", "+91 98765 43210", "new", "employee", "campaign-1", null, now.toEpochMilli(), "dashboard", 1)),
                nextCursor = "cursor-phase-8",
                serverTimestamp = now.toString(),
            )
        )

        val linked = dao.observeCall("unmatched-call").first()
        assertEquals("lead-live", linked?.leadId)
        assertEquals("campaign-1", linked?.campaignId)
        assertEquals(SyncStatus.PENDING.name, linked?.syncStatus)
        val update = dao.syncEvents("CALL", "unmatched-call").single()
        assertEquals("UPDATE", update.operation)
        assertTrue(update.payload.contains("matched_after_lead_assignment"))
        assertEquals("cursor-phase-8", cursorStore.current())
    }

    @Test fun duplicate_dashboard_numbers_do_not_auto_link_unmatched_history() = runTest {
        val dao = database.dao()
        dao.insertCall(CallEntity("ambiguous-call", null, null, "employee", null, "+919999999999", CallDirection.OUTGOING.name, now.toEpochMilli(), null, now.plusSeconds(10).toEpochMilli(), null, SyncStatus.SYNCED.name))
        val cursorStore = SyncCursorStore(context)
        val applier = DeltaSyncApplier(database, dao, cursorStore, clock)
        val shared = "+919999999999"
        applier.apply(DeltaSyncResponse(
            leads = listOf(
                LeadDeltaDto("lead-a", "server-a", "Lead A", null, null, shared, shared, "new", "employee", null, null, now.toEpochMilli(), "dashboard", 1),
                LeadDeltaDto("lead-b", "server-b", "Lead B", null, null, shared, shared, "new", "employee", null, null, now.toEpochMilli(), "dashboard", 1),
            ),
            nextCursor = "cursor-ambiguous",
            serverTimestamp = now.toString(),
        ))

        assertEquals(null, dao.observeCall("ambiguous-call").first()?.leadId)
        assertTrue(dao.syncEvents("CALL", "ambiguous-call").isEmpty())
    }

    private fun openDatabase() = Room.databaseBuilder(context, CallFlowDatabase::class.java, databaseName)
        .addMigrations(CallFlowDatabase.MIGRATION_1_2, CallFlowDatabase.MIGRATION_2_3)
        .build()
}
