package de.mm20.launcher2.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.ktx.tryStartActivity

sealed class Widget {
    abstract fun loadLabel(context: Context): String
    abstract fun toDatabaseEntity(position: Int = -1): WidgetEntity
    open val isConfigurable: Boolean = false
    open fun configure(context: Activity, appWidgetHost: AppWidgetHost) {}

    companion object {
        fun fromDatabaseEntity(context: Context, entity: WidgetEntity): Widget? {
            if (entity.type == WidgetType.INTERNAL.value) {
                return when (entity.data) {
                    "weather" -> WeatherWidget
                    "music" -> MusicWidget
                    "calendar" -> CalendarWidget
                    else -> null
                }
            } else {
                val widgetId = entity.data.toIntOrNull() ?: return null
                val widgetInfo =
                    AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return null
                return ExternalWidget(
                    height = entity.height,
                    widgetId = widgetId,
                    widgetProviderInfo = widgetInfo
                )
            }
        }
    }
}


object WeatherWidget : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_weather)
    }

    override fun toDatabaseEntity(position: Int): WidgetEntity {
        return WidgetEntity(
            type = WidgetType.INTERNAL.value,
            data = "weather",
            height = -1,
            position = position
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
            "settings/widgets/weather"
        )
        context.tryStartActivity(intent)
    }
}

object MusicWidget : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_music)
    }

    override fun toDatabaseEntity(position: Int): WidgetEntity {
        return WidgetEntity(
            type = WidgetType.INTERNAL.value,
            data = "music",
            height = -1,
            position = position
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
}


object CalendarWidget : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_calendar)
    }

    override fun toDatabaseEntity(position: Int): WidgetEntity {
        return WidgetEntity(
            type = WidgetType.INTERNAL.value,
            data = "calendar",
            height = -1,
            position = position
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
            "settings/widgets/calendar"
        )
        context.tryStartActivity(intent)
    }
}

class ExternalWidget(
    var height: Int,
    val widgetId: Int,
    val widgetProviderInfo: AppWidgetProviderInfo
) : Widget() {
    override fun loadLabel(context: Context): String {
        return widgetProviderInfo.loadLabel(context.packageManager)
    }

    override fun toDatabaseEntity(position: Int): WidgetEntity {
        return WidgetEntity(
            type = WidgetType.THIRD_PARTY.value,
            data = widgetId.toString(),
            height = height,
            position = position
        )
    }

    override val isConfigurable: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        widgetProviderInfo.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
    } else {
        false
    }

    override fun configure(context: Activity, appWidgetHost: AppWidgetHost) {
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            context,
            widgetId,
            0,
            0,
            null
        )
    }
}

enum class WidgetType(val value: String) {
    INTERNAL("internal"),
    THIRD_PARTY("3rdparty")
}