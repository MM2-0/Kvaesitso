package de.mm20.launcher2.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import de.mm20.launcher2.ktx.tryStartActivity
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CalendarWidgetConfig(
    val allDayEvents: Boolean = true,
    val excludedCalendarIds: List<Long> = emptyList(),
)
data class CalendarWidget(
    override val id: UUID,
    val config: CalendarWidgetConfig = CalendarWidgetConfig(),
) : Widget() {
    override fun loadLabel(context: Context): String {
        return context.getString(R.string.widget_name_calendar)
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
            "settings/widgets/calendar"
        )
        context.tryStartActivity(intent)
    }

    companion object {
        const val Type = "calendar"
    }
}