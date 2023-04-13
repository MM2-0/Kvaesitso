package de.mm20.launcher2.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.ktx.decodeFromStringOrNull
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.serialization.json.Json
import java.util.UUID

sealed class Widget {

    abstract val id: UUID
    abstract fun loadLabel(context: Context): String
    fun toDatabaseEntity(position: Int, parentId: UUID? = null): WidgetEntity {
        return toDatabaseEntity().let {
            WidgetEntity(
                id = it.id,
                type = it.type,
                config = it.config,
                position = position,
                parentId = parentId,
            )
        }
    }

    abstract fun toDatabaseEntity(): PartialWidgetEntity

    open val isConfigurable: Boolean = false
    open fun configure(context: Activity, appWidgetHost: AppWidgetHost) {}

    companion object {
        fun fromDatabaseEntity(context: Context, entity: WidgetEntity): Widget? {
            return when (entity.type) {
                WeatherWidget.Type -> {
                    val config: WeatherWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: WeatherWidgetConfig()
                    WeatherWidget(entity.id, config)
                }

                MusicWidget.Type -> MusicWidget(entity.id)
                CalendarWidget.Type -> {
                    val config: CalendarWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: CalendarWidgetConfig()
                    CalendarWidget(entity.id, config)
                }

                FavoritesWidget.Type -> FavoritesWidget(entity.id)
                AppWidget.Type -> {
                    val config: AppWidgetConfig =
                        Json.decodeFromStringOrNull(entity.config?.takeIf { it.isNotBlank() })
                            ?: return null
                    AppWidget(
                        entity.id,
                        config,
                        widgetProviderInfo = AppWidgetManager.getInstance(context)
                            .getAppWidgetInfo(config.widgetId)
                    )
                }

                else -> null
            }
        }
    }
}


data class MusicWidget(
    override val id: UUID,
) : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_music)
    }

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = null
        )
    }

    override val isConfigurable: Boolean = true

    override fun configure(context: Activity, appWidgetHost: AppWidgetHost) {
        val intent = Intent()
        intent.component = ComponentName(
            context.getPackageName(),
            "de.mm20.launcher2.ui.settings.SettingsActivity"
        )
        intent.putExtra(
            "de.mm20.launcher2.settings.ROUTE",
            "settings/widgets/music"
        )
        context.tryStartActivity(intent)
    }

    companion object {
        const val Type = "music"
    }
}


data class FavoritesWidget(
    override val id: UUID,
) : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_favorites)
    }

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = null,
        )
    }

    override val isConfigurable: Boolean = true

    override fun configure(context: Activity, appWidgetHost: AppWidgetHost) {
        val intent = Intent()
        intent.component = ComponentName(
            context.getPackageName(),
            "de.mm20.launcher2.ui.settings.SettingsActivity"
        )
        intent.putExtra(
            "de.mm20.launcher2.settings.ROUTE",
            "settings/favorites"
        )
        context.tryStartActivity(intent)
    }

    companion object {
        const val Type = "favorites"
    }
}


enum class WidgetType(val value: String) {
    INTERNAL("internal"),
    THIRD_PARTY("3rdparty")
}