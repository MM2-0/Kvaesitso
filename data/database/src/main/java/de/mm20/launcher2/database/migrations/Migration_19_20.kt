package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_19_20: Migration(19, 20) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Icons` RENAME TO `Icons_old`")
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `Icons` (
                `type` TEXT NOT NULL,
                `componentName` TEXT,
                `drawable` TEXT,
                `iconPack` TEXT NOT NULL,
                `name` TEXT,
                `themed` INTEGER NOT NULL DEFAULT 0,
                `id` INTEGER PRIMARY KEY AUTOINCREMENT)
            """)
        connection.execSQL("INSERT INTO `Icons` (`type`, `componentName`, `drawable`, `iconPack`, `themed`, `name`) SELECT `type`, `componentName`, `drawable`, `iconPack`, 0, null FROM `Icons_old`")
        connection.execSQL("DROP TABLE `Icons_old`")
        connection.execSQL("ALTER TABLE `IconPack` ADD COLUMN `themed` INTEGER NOT NULL DEFAULT 0")
    }
}