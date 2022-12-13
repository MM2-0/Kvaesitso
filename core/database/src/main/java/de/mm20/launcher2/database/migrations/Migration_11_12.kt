package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_11_12 : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Currency` (`symbol` TEXT NOT NULL, `value` REAL NOT NULL, `lastUpdate` INTEGER NOT NULL, PRIMARY KEY(`symbol`))")
    }
}