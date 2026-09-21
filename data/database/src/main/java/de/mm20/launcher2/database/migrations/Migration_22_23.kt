package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.mm20.launcher2.database.ktx.bindTextOrNull
import de.mm20.launcher2.ktx.toBytes
import org.koin.core.component.KoinComponent
import java.util.UUID

class Migration_22_23 : Migration(22, 23), KoinComponent {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Widget RENAME TO Widget_old")
        connection.execSQL(
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
            connection.prepare("SELECT `type`, `data`, `height`, `position` FROM `Widget_old`")
        while (oldWidgets.step()) {
            val oldType = oldWidgets.getText(0)
            val data = oldWidgets.getText(1)
            val newType = if (oldType == "3rdparty") "app" else data
            val height = oldWidgets.getInt(2)
            val position = oldWidgets.getInt(3)
            val id = UUID.randomUUID()
            val config = if (oldType == "3rdparty") {
                "{\"widgetId\": $data, \"height\": $height}"
            } else null

            connection.prepare("INSERT INTO `Widget` (`type`, `config`, `position`, `id`) VALUES (?, ?, ?, ?)").use {
                it.bindText(1, newType)
                it.bindTextOrNull(2, config)
                it.bindInt(3, position)
                it.bindBlob(4, id.toBytes())
                it.step()
            }
        }
        oldWidgets.close()
        connection.execSQL("DROP TABLE Widget_old")
    }
}