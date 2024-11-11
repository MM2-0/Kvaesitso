package de.mm20.launcher2.database.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sigpwned.emoji4j.core.Grapheme
import com.sigpwned.emoji4j.core.GraphemeMatcher
import de.mm20.launcher2.ktx.jsonObjectOf

class Migration_26_27 : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.d("Tags-Migration", "Migrating tags")
        db.query("SELECT DISTINCT value FROM CustomAttributes WHERE type = 'tag'").use { cursor ->
            while (cursor.moveToNext()) {
                val oldName = cursor.getString(0)
                val (emoji, newName) = oldName.splitLeadingEmoji()

                if (emoji != null) {
                    db.update(
                        "CustomAttributes",
                        SQLiteDatabase.CONFLICT_FAIL,
                        ContentValues().apply {
                            put("value", newName)
                        }, "type = 'tag' AND value = ?",
                        arrayOf(oldName)
                    )
                    db.insert(
                        "CustomAttributes",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("key", "tag://$newName")
                            put("type", "icon")
                            put("value", jsonObjectOf(
                                "type" to "custom_text_icon",
                                "text" to emoji,
                                "color" to 0,
                            ).toString())
                        }
                    )
                    db.update(
                        "Searchable",
                        SQLiteDatabase.CONFLICT_IGNORE,
                        ContentValues().apply {
                            put("key", "tag://$newName")
                            put("searchable", jsonObjectOf(
                                "tag" to newName
                            ).toString())
                        },
                        "key = ?",
                        arrayOf("tag://$oldName")
                    )
                }
            }
        }
    }

    fun String.splitLeadingEmoji(): Pair<String?, String> {
        val matcher = GraphemeMatcher(this)
        if (!matcher.find()) return null to this.trim()
        val grapheme = matcher.grapheme()
        if (grapheme?.type == Grapheme.Type.EMOJI && matcher.start() == 0) {
            val end = matcher.end()
            val emoji = this.substring(0, end)
            val tagName = this.substring(end).takeIf { it.isNotBlank() }
            if (tagName == null) return emoji to emoji
            return emoji to tagName
        }
        return null to this.trim()
    }
}