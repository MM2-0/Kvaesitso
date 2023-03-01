package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_21_22: Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE `Searchable`
            ADD `weight` DOUBLE DEFAULT 0.0
            """)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `LaunchTimestamp` (
                `stamp` BIGINT PRIMARY KEY NOT NULL,
                `key` TEXT NOT NULL)
            """)
    }
}