package de.mm20.launcher2.database.migrations

import androidx.core.database.getStringOrNull
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.mm20.launcher2.ktx.jsonObjectOf

class Migration_18_19 : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val websearches =
            database.query("SELECT label, urlTemplate, color, icon, encoding FROM `Websearch` ORDER BY label ASC")
        database.execSQL("CREATE TABLE IF NOT EXISTS `SearchAction` (`position` INTEGER NOT NULL, `type` TEXT NOT NULL, `data` TEXT NOT NULL, `label` TEXT, `icon` INTEGER NOT NULL, `color` INTEGER NOT NULL, `customIcon` TEXT, `options` TEXT, PRIMARY KEY(`position`))"
        )
        var position = 0
        while (websearches.moveToNext()) {
            val label = websearches.getString(0)
            val data = websearches.getString(1)
            val color = websearches.getInt(2)
            val icon = websearches.getStringOrNull(3)
            val encoding = websearches.getStringOrNull(4)

            val options = encoding?.let{
                jsonObjectOf("encoding" to encoding).toString()
            }

            database.execSQL(
                "INSERT INTO `SearchAction` (`position`, `type`, `data`, `label`, `color`, `icon`, `customIcon`, `options`)" +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    position,
                    "url",
                    data,
                    label,
                    color,
                    if (icon == null) 0 else 1,
                    icon,
                    options
                )
            )
            position++
        }
        database.execSQL("DROP TABLE `Websearch`")
    }
}