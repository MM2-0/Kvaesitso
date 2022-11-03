package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_9_10 : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Plugins` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `description` TEXT NOT NULL, `pluginClassName` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`packageName`) );")
    }

}