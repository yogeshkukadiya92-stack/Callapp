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

    @Test fun phase_one_dispositions_include_callback_and_custom_outcome() = runTest {
        val repository = OfflineCallRepository(database, database.dao(), clock)
        repository.seedDispositionsIfEmpty()
        val options = repository.observeDispositions().first()
        assertTrue(options.any { it.code == "CALLBACK_REQUESTED" && it.requiresFollowUp })
        assertTrue(options.any { it.code == "CUSTOM" && it.requiresNote })
    }

    private fun openDatabase() = Room.databaseBuilder(context, CallFlowDatabase::class.java, databaseName)
        .addMigrations(CallFlowDatabase.MIGRATION_1_2, CallFlowDatabase.MIGRATION_2_3)
        .build()
}
