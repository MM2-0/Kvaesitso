package de.mm20.launcher2.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class AppWidgetConfig(
    val widgetId: Int,
    val height: Int,
    val width: Int? = null,
    val borderless: Boolean = false,
    val background: Boolean = true,
    val themeColors: Boolean = true,
)

data class AppWidget(
    override val id: UUID,
    val config: AppWidgetConfig,
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    private var label: String? = null

    override fun getLabel(context: Context): String {
        if (label != null) return label!!
        val widgetInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(config.widgetId)
        if (widgetInfo == null) {
            label = ""
            return label!!
        }
        try {
            label = widgetInfo.loadLabel(context.packageManager)
        } catch (e: PackageManager.NameNotFoundException) {
            label = ""
        }
        return label!!
    }

    companion object {
        const val Type = "app"
    }
}