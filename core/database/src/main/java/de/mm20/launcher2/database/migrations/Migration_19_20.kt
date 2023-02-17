package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_19_20: Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Icons` RENAME TO `Icons_old`")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `Icons` (
                `type` TEXT NOT NULL,
                `componentName` TEXT,
                `drawable` TEXT,
                `iconPack` TEXT NOT NULL,
                `name` TEXT,
                `themed` INTEGER NOT NULL DEFAULT 0,
                `id` INTEGER PRIMARY KEY AUTOINCREMENT)
            """)
        database.execSQL("INSERT INTO `Icons` (`type`, `componentName`, `drawable`, `iconPack`, `themed`, `name`) SELECT `type`, `componentName`, `drawable`, `iconPack`, 0, null FROM `Icons_old`")
        database.execSQL("DROP TABLE `Icons_old`")
        database.execSQL("ALTER TABLE `IconPack` ADD COLUMN `themed` INTEGER NOT NULL DEFAULT 0")
    }
}