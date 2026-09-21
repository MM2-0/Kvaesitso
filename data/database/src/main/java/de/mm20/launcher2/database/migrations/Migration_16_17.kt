package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_16_17 : Migration(16, 17) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Websearch ADD COLUMN encoding INTEGER")
    }
}