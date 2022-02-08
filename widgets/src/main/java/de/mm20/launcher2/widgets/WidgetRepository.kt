package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.widgets.R
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors

class WidgetRepository(
        val context: Context
) {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    fun getWidgets(): Flow<List<Widget>> {
        return AppDatabase.getInstance(context).widgetDao()
            .getWidgets()
            .map { it.map { Widget(it) } }
    }

    fun getInternalWidgets(): List<Widget> {
        return listOf(
                Widget(WidgetType.INTERNAL, "weather", -1, context.getString(R.string.widget_name_weather)),
                Widget(WidgetType.INTERNAL, "music", -1, context.getString(R.string.widget_name_music)),
                Widget(WidgetType.INTERNAL, "calendar", -1, context.getString(R.string.widget_name_calendar)),
        )
    }


    fun saveWidgets(widgets: List<Widget>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).widgetDao().updateWidgets(widgets.mapIndexed { i, widget -> widget.toDatabaseEntity(i) })
            }
        }
    }
}