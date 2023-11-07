package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal class Migration_25_26 : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
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