package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_12_13 : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Plugin` (`packageName` TEXT NOT NULL, `data` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`packageName`, `data`))")
    }
}