package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_6_7 : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE Searchable2 (`key` TEXT NOT NULL, `searchable` TEXT, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `inAllApps` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        connection.execSQL("INSERT INTO Searchable2 SELECT * FROM Searchable")
        connection.execSQL("DROP TABLE Searchable")
        connection.execSQL("ALTER TABLE Searchable2 RENAME TO Searchable")
    }

}