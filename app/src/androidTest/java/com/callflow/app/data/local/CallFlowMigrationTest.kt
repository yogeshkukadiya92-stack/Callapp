package com.callflow.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CallFlowMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CallFlowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migratesVersion1To6WithoutDataLoss() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO leads (id, serverId, name, company, city, normalizedPhone, displayPhone, stageId, assignedUserId, campaignId, nextFollowUpAt, updatedAt, updatedBy, version) VALUES ('lead-1', NULL, 'Migration Lead', NULL, NULL, '+919999999999', '9999999999', 'new', 'user-1', NULL, NULL, 1, 'user-1', 1)")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE_NAME, 6, true, CallFlowDatabase.MIGRATION_1_2, CallFlowDatabase.MIGRATION_2_3, CallFlowDatabase.MIGRATION_3_4, CallFlowDatabase.MIGRATION_4_5, CallFlowDatabase.MIGRATION_5_6).use { database ->
            database.query("SELECT name, score, quality FROM leads WHERE id = 'lead-1'").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.getString(0) == "Migration Lead")
                check(cursor.getInt(1) == 0)
                check(cursor.isNull(2))
            }
            database.query("SELECT COUNT(*) FROM sync_conflicts").use { cursor -> check(cursor.moveToFirst() && cursor.getInt(0) == 0) }
        }
    }

    companion object { private const val DATABASE_NAME = "migration-test" }

    @Test fun migratesNotesToVersion8WithoutLosingExistingNotes() {
        helper.createDatabase("notes-migration-test", 7).apply {
            execSQL("INSERT INTO notes VALUES ('note-existing', 'lead-1', 'call-1', 'Keep this note', 1, 'user', 'device', 'PENDING')")
            close()
        }
        helper.runMigrationsAndValidate("notes-migration-test", 8, true, CallFlowDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT body FROM notes WHERE id = 'note-existing'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "Keep this note")
            }
            db.execSQL("INSERT INTO notes VALUES ('unmatched-note', NULL, 'call-2', 'Unmatched caller note', 2, 'user', 'device', 'PENDING')")
        }
    }
}
