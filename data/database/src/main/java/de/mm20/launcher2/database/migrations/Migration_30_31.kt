package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.ktx.toBytes
import de.mm20.launcher2.preferences.WidgetScreenTarget

class Migration_30_31 : Migration(30, 31) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE Widget 
            SET parentId = ? 
            WHERE parentId IS NULL    
            """.trimIndent(),
            arrayOf(WidgetScreenTarget.Default.scopeId.toBytes()),
        )
    }
}
