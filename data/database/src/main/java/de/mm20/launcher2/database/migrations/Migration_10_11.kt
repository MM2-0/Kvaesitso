package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_10_11 : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `temp` (`key` TEXT NOT NULL, `searchable` TEXT NOT NULL, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        connection.execSQL("INSERT INTO `temp` SELECT `key`, `searchable`, `launchCount`, `pinned`, `hidden` FROM `Searchable`")
        connection.execSQL("DROP TABLE `Searchable`")
        connection.execSQL("ALTER TABLE `temp` RENAME TO `Searchable`")
    }
}