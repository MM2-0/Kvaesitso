package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal class Migration_25_26 : Migration(25, 26) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE Plugins
            (
                authority TEXT NOT NULL,
                label TEXT NOT NULL,
                description TEXT,
                packageName TEXT NOT NULL,
                className TEXT NOT NULL,
                type TEXT NOT NULL,
                settingsActivity TEXT,
                enabled INTEGER NOT NULL,
                PRIMARY KEY(`authority`)
            )
        """.trimIndent()
        )
    }
}