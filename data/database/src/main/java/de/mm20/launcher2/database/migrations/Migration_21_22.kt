package de.mm20.launcher2.database.migrations

import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.mm20.launcher2.database.ktx.getIntOrNull

class Migration_21_22 : Migration(21, 22) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE `Searchable`
            ADD `weight` DOUBLE NOT NULL DEFAULT 0.0
            """
        )

        connection.prepare("SELECT MAX(`launchCount`) FROM `Searchable`").use {
            if (!it.step()) return@use

            val launchCount = it.getIntOrNull(0) ?: return@use

            connection.execSQL("UPDATE `Searchable` SET `weight` = `launchCount` / $launchCount")
        }
    }
}