package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_10_11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `temp` (`key` TEXT NOT NULL, `searchable` TEXT NOT NULL, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        database.execSQL("INSERT INTO `temp` SELECT `key`, `searchable`, `launchCount`, `pinned`, `hidden` FROM `Searchable`")
        database.execSQL("DROP TABLE `Searchable`")
        database.execSQL("ALTER TABLE `temp` RENAME TO `Searchable`")
    }
}