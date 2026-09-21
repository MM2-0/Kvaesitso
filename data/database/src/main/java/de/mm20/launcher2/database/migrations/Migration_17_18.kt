package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_17_18 : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Searchable ADD COLUMN type TEXT NOT NULL DEFAULT ''")
        connection.execSQL(
            """
            UPDATE Searchable
            SET type = SUBSTR(`key`, 0, INSTR(`key`, '://')),
            searchable = SUBSTR(`searchable`, INSTR(`searchable`, '#') + 1)
            """.trimIndent()
        )
    }
}