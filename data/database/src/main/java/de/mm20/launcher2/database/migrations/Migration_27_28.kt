package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_27_28: Migration(27, 28) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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