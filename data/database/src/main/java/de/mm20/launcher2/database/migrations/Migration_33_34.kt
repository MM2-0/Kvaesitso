package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_33_34 : Migration(33, 34) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `SearchAction` WHERE `type` = 'private_space'")
    }
}
