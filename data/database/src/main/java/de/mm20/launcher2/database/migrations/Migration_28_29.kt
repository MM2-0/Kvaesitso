package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_28_29: Migration(28, 29) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Transparencies` (
                `id` BLOB NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `background` REAL,
                `surface` REAL,
                `elevatedSurface` REAL
            )
            """.trimIndent()
        )
    }
}