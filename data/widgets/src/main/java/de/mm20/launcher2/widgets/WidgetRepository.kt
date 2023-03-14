package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import java.io.File

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

    fun isFavoritesWidgetFirst(): Flow<Boolean>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)
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

    override fun isFavoritesWidgetFirst(): Flow<Boolean> {
        return database.widgetDao().getFirst().map { it?.type == "internal" && it.data == "favorites" }
    }

    override suspend fun export(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        do {
            val widgets = dao.exportWidgets(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (widget in widgets) {
                if (widget.type != WidgetType.INTERNAL.value) continue
                jsonArray.put(
                    jsonObjectOf(
                        "data" to widget.data,
                        "position" to widget.position,
                    )
                )
            }

            val file = File(toDir, "widgets.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (widgets.size == 100)
    }

    override suspend fun import(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeWidgets()

        val files = fromDir.listFiles { _, name -> name.startsWith("widgets.") } ?: return@withContext

        for (file in files) {
            val widgets = mutableListOf<WidgetEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = WidgetEntity(
                        type = WidgetType.INTERNAL.value,
                        position = json.getInt("position"),
                        data = json.getString("data"),
                        height = -1,
                    )
                    widgets.add(entity)
                }

                dao.importWidgets(widgets)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }
}