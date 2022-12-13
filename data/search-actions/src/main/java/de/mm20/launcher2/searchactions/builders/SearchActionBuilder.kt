package de.mm20.launcher2.searchactions.builders

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.metrics.Event
import de.mm20.launcher2.database.entities.SearchActionEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.searchactions.TextClassificationResult
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import org.json.JSONException
import org.json.JSONObject

interface SearchActionBuilder {
    val label: String
    val icon: SearchActionIcon
    val iconColor: Int
        get() = 0
    val customIcon: String?
        get() = null

    val key: String
    fun build(context: Context, classifiedQuery: TextClassificationResult): SearchAction?

    companion object {
        internal fun from(context: Context, entity: SearchActionEntity): SearchActionBuilder? {
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
                        urlTemplate = entity.data ?: return null,
                        iconColor = entity.color ?: 0,
                        icon = SearchActionIcon.fromInt(entity.icon),
                        customIcon = entity.customIcon,
                        encoding = WebsearchActionBuilder.QueryEncoding.fromInt(options?.optInt("encoding"))
                    )
                }
                "app" -> {
                    return AppSearchActionBuilder(
                        label = entity.label ?: "",
                        baseIntent = Intent.parseUri(entity.data, 0),
                        iconColor = entity.color ?: 0,
                        icon = SearchActionIcon.fromInt(entity.icon),
                        customIcon = entity.customIcon,
                    )
                }
                "intent" -> {
                    return CustomIntentActionBuilder(
                        entity.label ?: "",
                        baseIntent = Intent.parseUri(entity.data, 0),
                        iconColor = entity.color ?: 0,
                        icon = SearchActionIcon.fromInt(entity.icon),
                        customIcon = entity.customIcon,
                        queryKey = options?.getString("extra")?.takeIf { it.isNotEmpty() } ?: return null
                    )
                }
                "call" -> return CallActionBuilder(context)
                "message" -> return MessageActionBuilder(context)
                "email" -> return EmailActionBuilder(context)
                "contact" -> return CreateContactActionBuilder(context)
                "alarm" -> return SetAlarmActionBuilder(context)
                "timer" -> return TimerActionBuilder(context)
                "calendar" -> return ScheduleEventActionBuilder(context)
                "website" -> return OpenUrlActionBuilder(context)
                else -> return null
            }
        }

        internal fun toDatabaseEntity(builder: SearchActionBuilder, position: Int): SearchActionEntity {
            return when(builder) {
                is WebsearchActionBuilder -> SearchActionEntity(
                    position = position,
                    type = "url",
                    label = builder.label,
                    data = builder.urlTemplate,
                    color = builder.iconColor,
                    icon = builder.icon.toInt(),
                    customIcon = builder.customIcon,
                    options = jsonObjectOf(
                        "encoding" to builder.encoding.toInt()
                    ).toString()
                )
                is AppSearchActionBuilder -> SearchActionEntity(
                    position = position,
                    type = "app",
                    label = builder.label,
                    data = builder.baseIntent.toUri(0),
                    color = builder.iconColor,
                    icon = builder.icon.toInt(),
                    customIcon = builder.customIcon,
                    options = null
                )
                is CustomIntentActionBuilder -> SearchActionEntity(
                    position = position,
                    type = "intent",
                    label = builder.label,
                    data = builder.baseIntent.toUri(0),
                    color = builder.iconColor,
                    icon = builder.icon.toInt(),
                    customIcon = builder.customIcon,
                    options = jsonObjectOf(
                        "extra" to builder.queryKey
                    ).toString()
                )
                else -> SearchActionEntity(
                    position = position,
                    type = builder.key,
                )
            }
        }
    }
}
