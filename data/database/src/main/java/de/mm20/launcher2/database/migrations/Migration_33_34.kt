package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_33_34 : Migration(33, 34) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DELETE FROM `SearchAction` WHERE `type` = 'private_space'")
    }
}
