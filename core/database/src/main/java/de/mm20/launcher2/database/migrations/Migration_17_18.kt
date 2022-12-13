package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_17_18 : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Searchable ADD COLUMN type TEXT NOT NULL DEFAULT ''")
        database.execSQL(
            """
            UPDATE Searchable
            SET type = SUBSTR(`key`, 0, INSTR(`key`, '://')),
            searchable = SUBSTR(`searchable`, INSTR(`searchable`, '#') + 1)
            """.trimIndent()
        )
    }
}