package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_34_35 : Migration(34, 35) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Searchable` ADD COLUMN `excludeFromFrequentlyUsed` INTEGER NOT NULL DEFAULT 0")
    }
}
