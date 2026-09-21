package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_15_16 : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `CustomAttributes` (
                `key` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `id` INTEGER PRIMARY KEY AUTOINCREMENT
            )
            """.trimIndent()
        )
    }
}