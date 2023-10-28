package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_20_21: Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `Icons`")
        database.execSQL("DELETE FROM `IconPack`")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `Icons` (
                `type` TEXT NOT NULL,
                `packageName` TEXT,
                `activityName` TEXT,
                `drawable` TEXT,
                `extras` TEXT,
                `iconPack` TEXT NOT NULL,
                `name` TEXT,
                `themed` INTEGER NOT NULL DEFAULT 0,
                `id` INTEGER PRIMARY KEY AUTOINCREMENT)
            """)
    }
}