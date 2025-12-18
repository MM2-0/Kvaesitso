package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_29_30 : Migration(29, 30) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Typography` (
                `id` BLOB NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `fonts` TEXT,
                `displayLarge` TEXT,
                `displayMedium` TEXT,
                `displaySmall` TEXT,
                `headlineLarge` TEXT,
                `headlineMedium` TEXT,
                `headlineSmall` TEXT,
                `titleLarge` TEXT,
                `titleMedium` TEXT,
                `titleSmall` TEXT,
                `bodyLarge` TEXT,
                `bodyMedium` TEXT,
                `bodySmall` TEXT,
                `labelLarge` TEXT,
                `labelMedium` TEXT,
                `labelSmall` TEXT,
                `emphasizedDisplayLarge` TEXT,
                `emphasizedDisplayMedium` TEXT,
                `emphasizedDisplaySmall` TEXT,
                `emphasizedHeadlineLarge` TEXT,
                `emphasizedHeadlineMedium` TEXT,
                `emphasizedHeadlineSmall` TEXT,
                `emphasizedTitleLarge` TEXT,
                `emphasizedTitleMedium` TEXT,
                `emphasizedTitleSmall` TEXT,
                `emphasizedBodyLarge` TEXT,
                `emphasizedBodyMedium` TEXT,
                `emphasizedBodySmall` TEXT,
                `emphasizedLabelLarge` TEXT,
                `emphasizedLabelMedium` TEXT,
                `emphasizedLabelSmall` TEXT
            )
            """.trimIndent()
        )
    }
}