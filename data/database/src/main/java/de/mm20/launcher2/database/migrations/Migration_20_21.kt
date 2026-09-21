package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_20_21: Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE `Icons`")
        connection.execSQL("DELETE FROM `IconPack`")
        connection.execSQL("""
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