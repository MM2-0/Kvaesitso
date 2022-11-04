package de.mm20.launcher2.searchactions.builders

import android.content.Context
import de.mm20.launcher2.database.entities.SearchActionEntity
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import org.json.JSONException
import org.json.JSONObject

interface SearchActionBuilder {
    fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction?

    companion object {
        fun from(entity: SearchActionEntity): SearchActionBuilder? {
            val options = entity.options?.let {
                try {
                    JSONObject(it)
                } catch (_: JSONException) {
                    null
                }
            }
            when (entity.type) {
                "url" -> {
                    return WebsearchActionBuilder(
                        label = entity.label ?: "",
                        urlTemplate = entity.data,
                        color = entity.color,
                        icon = SearchActionIcon.fromInt(entity.icon),
                        customIcon = entity.customIcon,
                        encoding = WebsearchActionBuilder.QueryEncoding.fromInt(options?.optInt("encoding"))
                    )
                }
                "app" -> {
                    return null
                }
                else -> return null
            }
        }
    }

}
