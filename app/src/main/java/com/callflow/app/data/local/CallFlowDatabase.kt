package com.callflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LeadEntity::class, CallEntity::class, CallEventEntity::class, NoteEntity::class, FollowUpEntity::class, SyncEventEntity::class, AppConfigurationEntity::class, LeadStageEntity::class, DispositionEntity::class, CallDispositionEntity::class, SyncConflictEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CallFlowDatabase : RoomDatabase() {
    abstract fun dao(): CallFlowDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `call_dispositions` (`id` TEXT NOT NULL, `callId` TEXT NOT NULL, `leadId` TEXT NOT NULL, `dispositionId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdBy` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_call_dispositions_callId` ON `call_dispositions` (`callId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_dispositions_leadId` ON `call_dispositions` (`leadId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_dispositions_createdAt` ON `call_dispositions` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_dispositions_syncStatus` ON `call_dispositions` (`syncStatus`)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_conflicts` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `localVersion` INTEGER NOT NULL, `serverVersion` INTEGER NOT NULL, `localPayload` TEXT NOT NULL, `serverPayload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_entityType` ON `sync_conflicts` (`entityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_entityId` ON `sync_conflicts` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_status` ON `sync_conflicts` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_createdAt` ON `sync_conflicts` (`createdAt`)")
            }
        }
    }
}
