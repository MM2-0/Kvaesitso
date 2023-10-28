package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_6_7 : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE Searchable2 (`key` TEXT NOT NULL, `searchable` TEXT, `launchCount` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `inAllApps` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        database.execSQL("INSERT INTO Searchable2 SELECT * FROM Searchable")
        database.execSQL("DROP TABLE Searchable")
        database.execSQL("ALTER TABLE Searchable2 RENAME TO Searchable")
    }

}