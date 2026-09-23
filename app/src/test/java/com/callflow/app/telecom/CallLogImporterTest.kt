package com.callflow.app.telecom

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.callflow.app.core.phone.CountryAwarePhoneNumberNormalizer
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.session.CallLogCursorStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallLogImporterTest {
    @Test fun batchedImportPreservesEveryCallAndIsIdempotent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALL_LOG)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
                val since = args?.first()?.toLong() ?: 0
                return MatrixCursor(projection).apply {
                    for (index in 1..301) {
                        val date = 1_000_000L + (index / 2) * 1000L
                        if (date >= since) addRow(arrayOf(index.toLong(), "2025550100", CallLog.Calls.OUTGOING_TYPE, date, 12L, null, null))
                    }
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, provider)
        val db = Room.inMemoryDatabaseBuilder(context, CallFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val cursor = CallLogCursorStore(context)
            cursor.updateLastImportedAt(0)
            val importer = CallLogImporter(context, db.dao(), CountryAwarePhoneNumberNormalizer(), cursor, PostCallCoordinator(context), db)
            importer.importNewCalls(250)
            importer.importNewCalls(250)
            assertEquals(301, db.dao().observeRecentCalls(1000).first().size)
            assertEquals(0, importer.importNewCalls(250))
            assertTrue(db.dao().observeRecentCalls(1000).first().all { it.endedAt!! - it.answeredAt!! == 12000L })
        } finally { db.close() }
    }

    @Test fun specificSimSyncFiltersOutCallsFromOtherSim() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
                val since = args?.first()?.toLong() ?: 0
                return MatrixCursor(projection).apply {
                    // Call 1 on SIM 1 (phoneAccountId = "0" -> slot 1)
                    addRow(arrayOf(1L, "9876543210", CallLog.Calls.OUTGOING_TYPE, 1_000_000L, 20L, "0", null))
                    // Call 2 on SIM 2 (phoneAccountId = "1" -> slot 2)
                    addRow(arrayOf(2L, "9876543211", CallLog.Calls.OUTGOING_TYPE, 1_005_000L, 25L, "1", null))
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal(CallLog.AUTHORITY, provider)
        val db = Room.inMemoryDatabaseBuilder(context, CallFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val cursor = CallLogCursorStore(context)
            cursor.updateLastImportedAt(0)
            val simStore = com.callflow.app.data.session.SimPreferenceStore(context)
            // Configure to only sync SIM 1
            simStore.setSimSyncSlot(1)

            val importer = CallLogImporter(context, db.dao(), CountryAwarePhoneNumberNormalizer(), cursor, PostCallCoordinator(context), db, simStore)
            importer.importNewCalls(10)

            val importedCalls = db.dao().observeRecentCalls(10).first()
            // Only SIM 1 call should be imported
            assertEquals(1, importedCalls.size)
            assertEquals("+919876543210", importedCalls.first().normalizedPhone)
            assertEquals(1, importedCalls.first().simSlot)

            // Outbox should only have 1 event for SIM 1 call
            val pendingEvents = db.dao().pendingSyncEvents(10)
            assertEquals(1, pendingEvents.size)
            assertTrue(pendingEvents.first().payload.contains("9876543210"))
        } finally { db.close() }
    }
}
