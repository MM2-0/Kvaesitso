package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.ktx.toBytes
import org.koin.core.component.KoinComponent
import java.util.UUID

class Migration_22_23 : Migration(22, 23), KoinComponent {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE Widget RENAME TO Widget_old")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Widget` (
                `type` TEXT NOT NULL,
                `config` TEXT,
                `position` INTEGER NOT NULL,
                `id` BLOB NOT NULL,
                `parentId` BLOB,
                PRIMARY KEY(`id`)
            )
        """
        )
        val oldWidgets =
            database.query("SELECT `type`, `data`, `height`, `position` FROM `Widget_old`")
        while (oldWidgets.moveToNext()) {
            val oldType = oldWidgets.getString(0)
            val data = oldWidgets.getString(1)
            val newType = if (oldType == "3rdparty") "app" else data
            val height = oldWidgets.getInt(2)
            val position = oldWidgets.getInt(3)
            val id = UUID.randomUUID()
            val config = if (oldType == "3rdparty") {
                "{\"widgetId\": $data, \"height\": $height}"
            } else null
            database.execSQL(
                "INSERT INTO `Widget` (`type`, `config`, `position`, `id`) VALUES (?, ?, ?, ?)",
                arrayOf(newType, config, position, id.toBytes())
            )
        }
        oldWidgets.close()
        database.execSQL("DROP TABLE Widget_old")
    }
}