package de.mm20.launcher2.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.mm20.launcher2.database.ktx.bindTextOrNull
import de.mm20.launcher2.database.ktx.getTextOrNull
import de.mm20.launcher2.ktx.jsonObjectOf

class Migration_18_19 : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        val websearches = connection.prepare("SELECT label, urlTemplate, color, icon, encoding FROM `Websearch` ORDER BY label ASC")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `SearchAction` (`position` INTEGER NOT NULL, `type` TEXT NOT NULL, `data` TEXT, `label` TEXT, `icon` INTEGER, `color` INTEGER, `customIcon` TEXT, `options` TEXT, PRIMARY KEY(`position`))"
        )
        connection.execSQL(
            "INSERT INTO `SearchAction` (`position`, `type`) VALUES" +
                    "(0, 'call')," +
                    "(1, 'message')," +
                    "(2, 'email')," +
                    "(3, 'contact')," +
                    "(4, 'alarm')," +
                    "(5, 'timer')," +
                    "(6, 'calendar')," +
                    "(7, 'website')"
        )
        var position = 8
        while (websearches.step()) {
            val label = websearches.getText(0)
            val data = websearches.getText(1)
            val color = 0
            val icon = websearches.getTextOrNull(3)
            val encoding = websearches.getTextOrNull(4)

            val options = encoding?.let {
                jsonObjectOf("encoding" to encoding).toString()
            }

            connection.prepare(
                "INSERT INTO `SearchAction` (`position`, `type`, `data`, `label`, `color`, `icon`, `customIcon`, `options`)" +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.bindInt(1, position)
                statement.bindText(2, "url")
                statement.bindText(3, data)
                statement.bindText(4, label)
                statement.bindInt(5, color)
                statement.bindInt(6, if (icon == null) 0 else 1)
                statement.bindTextOrNull(7, icon)
                statement.bindTextOrNull(8, options)
            }

            connection.prepare(
                "INSERT INTO `SearchAction` (`position`, `type`, `data`, `label`, `color`, `icon`, `customIcon`, `options`)" +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use {
                it.bindInt(1, position)
                it.bindText(2, "url")
                it.bindText(3, data)
                it.bindText(4, label)
                it.bindInt(5, color)
                it.bindInt(6, if (icon == null) 0 else 1)
                it.bindTextOrNull(7, icon)
                it.bindTextOrNull(8, options)

                it.step()
            }
            position++
        }
        websearches.close()
        connection.execSQL("DROP TABLE `Websearch`")
    }
}