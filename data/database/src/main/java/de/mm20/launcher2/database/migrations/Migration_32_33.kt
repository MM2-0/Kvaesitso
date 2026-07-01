package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_32_33 : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO `SearchAction` (`position`, `type`) VALUES " +
                    "((SELECT COALESCE(MIN(`position`) - 1, 0) FROM `SearchAction`), 'private_space')"
        )
    }
}
