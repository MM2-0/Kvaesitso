package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.preferences.WidgetScreenTarget

class Migration_31_32 : Migration(31, 32) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE `forecasts` ADD COLUMN `uvIndex` REAL NOT NULL DEFAULT -1.0;
            """.trimIndent(),
        )
    }
}
