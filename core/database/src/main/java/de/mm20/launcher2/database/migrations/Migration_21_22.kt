package de.mm20.launcher2.database.migrations

import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration_21_22: Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE `Searchable`
            ADD `weight` DOUBLE NOT NULL DEFAULT 0.0
            """)

        database.query("""
            SELECT MAX(`launchCount`) 
            FROM `Searchable`
            """)
            .runCatching {

                if (!this.moveToFirst()) {
                    return
                }

                this.getIntOrNull(0)
                    ?.run {
                        database.execSQL("""
                            UPDATE `Searchable` 
                            SET `weight` = `launchCount` / $this
                            """)
                    }

            }.onFailure {
                Log.e("Migration_21_22", "Setting default values for weight failed", it)
            }
    }
}