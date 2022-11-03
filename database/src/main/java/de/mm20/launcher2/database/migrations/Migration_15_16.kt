package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_15_16 : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
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