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

    companion object {
        const val Type = "app"
    }
}