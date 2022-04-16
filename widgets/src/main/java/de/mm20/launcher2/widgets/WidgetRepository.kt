package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface WidgetRepository {
    fun getWidgets(): Flow<List<Widget>>
    fun getInternalWidgets(): List<Widget>
    fun saveWidgets(widgets: List<Widget>)
    fun addWidget(widget: Widget, position: Int)
    fun removeWidget(widget: Widget)
    fun setWidgetHeight(widget: Widget, newHeight: Int)
    fun isWeatherWidgetEnabled(): Flow<Boolean>
    fun isMusicWidgetEnabled(): Flow<Boolean>
    fun isCalendarWidgetEnabled(): Flow<Boolean>
    fun isFavoritesWidgetEnabled(): Flow<Boolean>
}

internal class WidgetRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
) : WidgetRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getWidgets(): Flow<List<Widget>> {
        return database.widgetDao()
            .getWidgets()
            .map { it.mapNotNull { Widget.fromDatabaseEntity(context, it) } }
    }

    override fun getInternalWidgets(): List<Widget> {
        return listOf(WeatherWidget, MusicWidget, CalendarWidget, FavoritesWidget)
    }


    override fun saveWidgets(widgets: List<Widget>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.widgetDao()
                    .updateWidgets(widgets.mapIndexed { i, widget -> widget.toDatabaseEntity(i) })
            }
        }
    }

    override fun addWidget(widget: Widget, position: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.widgetDao()
                    .insert(widget.toDatabaseEntity(position))
            }
        }
    }

    override fun removeWidget(widget: Widget) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val ent = widget.toDatabaseEntity()
                database.widgetDao().deleteWidget(
                    ent.type,
                    ent.data
                )
            }
        }
    }

    override fun setWidgetHeight(widget: Widget, newHeight: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val ent = widget.toDatabaseEntity()
                database.widgetDao().updateHeight(
                    ent.type,
                    ent.data,
                    newHeight
                )
            }
        }
    }

    override fun isWeatherWidgetEnabled(): Flow<Boolean> {
        return database.widgetDao().exists("internal", "weather")
    }

    override fun isMusicWidgetEnabled(): Flow<Boolean> {
        return database.widgetDao().exists("internal", "music")
    }

    override fun isCalendarWidgetEnabled(): Flow<Boolean> {
        return database.widgetDao().exists("internal", "calendar")
    }

    override fun isFavoritesWidgetEnabled(): Flow<Boolean> {
        return database.widgetDao().exists("internal", "favorites")
    }

}