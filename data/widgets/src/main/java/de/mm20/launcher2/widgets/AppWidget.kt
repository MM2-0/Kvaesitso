package de.mm20.launcher2.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
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
)

data class AppWidget(
    override val id: UUID,
    val config: AppWidgetConfig,
    val widgetProviderInfo: AppWidgetProviderInfo
) : Widget() {
    override fun loadLabel(context: Context): String {
        return widgetProviderInfo.loadLabel(context.packageManager)
    }

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
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
            config.widgetId,
            0,
            0,
            null
        )
    }

    companion object {
        const val Type = "app"
    }
}