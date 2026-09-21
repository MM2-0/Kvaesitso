package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_9_10 : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Plugins` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `description` TEXT NOT NULL, `pluginClassName` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`packageName`) );")
    }

}