package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_23_24 : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Searchable RENAME TO Searchable_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Searchable` (
                `key` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `searchable` TEXT NOT NULL,
                `launchCount` INTEGER NOT NULL DEFAULT 0,
                `pinPosition` INTEGER NOT NULL DEFAULT 0,
                `hidden` INTEGER NOT NULL DEFAULT 0,
                `weight` DOUBLE NOT NULL DEFAULT 0.0,
                PRIMARY KEY(`key`)
            )
        """
        )
        database.execSQL(
            """
            INSERT INTO `Searchable` (`key`, `type`, `searchable`, `launchCount`, `pinPosition`, `hidden`, `weight`)
            SELECT `key`, `type`, `searchable`, `launchCount`, `pinned`, `hidden`, `weight` FROM `Searchable_old`
            """
        )
        database.execSQL("DROP TABLE Searchable_old")
    }
}