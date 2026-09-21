package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_32_33 : Migration(32, 33) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT INTO `SearchAction` (`position`, `type`) VALUES " +
                    "((SELECT COALESCE(MIN(`position`) - 1, 0) FROM `SearchAction`), 'private_space')"
        )
    }
}
