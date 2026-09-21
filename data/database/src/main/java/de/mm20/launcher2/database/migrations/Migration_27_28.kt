package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_27_28: Migration(27, 28) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Shapes` (
                `id` BLOB NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `baseShape` TEXT NOT NULL,
                `extraSmall` TEXT,
                `small` TEXT,
                `medium` TEXT,
                `large` TEXT,
                `largeIncreased` TEXT,
                `extraLarge` TEXT,
                `extraLargeIncreased` TEXT,
                `extraExtraLarge` TEXT
            )
            """.trimIndent()
        )
    }
}