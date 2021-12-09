package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.widgets.R
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class WidgetRepository(
        val context: Context
) {

    suspend fun getWidgets(): List<Widget> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).widgetDao().getWidgets().map { Widget(it) }
        }
    }

    fun getInternalWidgets(): List<Widget> {
        return listOf(
                Widget(WidgetType.INTERNAL, "weather", -1, context.getString(R.string.widget_name_weather)),
                Widget(WidgetType.INTERNAL, "music", -1, context.getString(R.string.widget_name_music)),
                Widget(WidgetType.INTERNAL, "calendar", -1, context.getString(R.string.widget_name_calendar)),
        )
    }


    suspend fun saveWidgets(widgets: List<Widget>) {
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).widgetDao().updateWidgets(widgets.mapIndexed { i, widget -> widget.toDatabaseEntity(i) })
        }
    }
}