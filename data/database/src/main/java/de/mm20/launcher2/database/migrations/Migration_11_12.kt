package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_11_12 : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Currency` (`symbol` TEXT NOT NULL, `value` REAL NOT NULL, `lastUpdate` INTEGER NOT NULL, PRIMARY KEY(`symbol`))")
    }
}