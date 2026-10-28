package com.fieldpin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SiteEntity::class, InspectionEntity::class, SiteDocumentEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FieldPinDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun siteDocumentDao(): SiteDocumentDao

    companion object {
        /** v2 adds site document attachments. Existing sites and inspections are untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_documents` (" +
                        "`id` TEXT NOT NULL, `siteId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `path` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_documents_siteId` ON `site_documents` (`siteId`)")
            }
        }

        fun build(context: Context): FieldPinDatabase =
            Room.databaseBuilder(context, FieldPinDatabase::class.java, "fieldpin.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
