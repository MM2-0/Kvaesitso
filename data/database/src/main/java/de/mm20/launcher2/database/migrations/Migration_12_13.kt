package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_12_13 : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Plugin` (`packageName` TEXT NOT NULL, `data` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`packageName`, `data`))")
    }
}