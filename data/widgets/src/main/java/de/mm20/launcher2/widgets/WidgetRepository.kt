package de.mm20.launcher2.widgets

import androidx.room.withTransaction
import de.mm20.launcher2.backup.Backupable
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
import java.util.UUID

interface WidgetRepository: Backupable {
    fun get(parent: UUID? = null, limit: Int = 100, offset: Int = 0): Flow<List<Widget>>
    fun update(widget: Widget)
    fun create(widget: Widget, position: Int, parentId: UUID? = null)
    fun delete(widget: Widget)
    fun set(widgets: List<Widget>, parentId: UUID? = null)

    fun exists(type: String): Flow<Boolean>
    fun count(type: String): Flow<Int>
}

internal class WidgetRepositoryImpl(
    private val database: AppDatabase,
) : WidgetRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    override fun get(parent: UUID?, limit: Int, offset: Int): Flow<List<Widget>> {
        val dao = database.widgetDao()
        return if (parent == null) {
            dao.queryRoot(limit, offset)
        } else {
            dao.queryByParent(parent, limit, offset)
        }.map {
            it.mapNotNull { Widget.fromDatabaseEntity(it) }
        }
    }

    override fun update(widget: Widget) {
        val dao = database.widgetDao()
        scope.launch {
            dao.patch(widget.toDatabaseEntity())
        }
    }

    override fun create(widget: Widget, position: Int, parentId: UUID?) {
        val dao = database.widgetDao()
        scope.launch {
            val entity = widget.toDatabaseEntity(position = position, parentId = parentId)
            dao.insert(entity)
        }
    }

    override fun delete(widget: Widget) {
        val dao = database.widgetDao()
        scope.launch {
            dao.delete(widget.id)
        }
    }

    override fun set(widgets: List<Widget>, parentId: UUID?) {
        val dao = database.widgetDao()
        scope.launch {
            database.withTransaction {
                if (parentId == null) {
                    dao.deleteRoot()
                } else {
                    dao.deleteByParent(parentId)
                }
                dao.insert(widgets.mapIndexed { index, widget ->
                    widget.toDatabaseEntity(position = index, parentId = parentId)
                })
            }
        }
    }

    override fun exists(type: String): Flow<Boolean> {
        val dao = database.widgetDao()
        return dao.exists(type = type)
    }

    override fun count(type: String): Flow<Int> {
        val dao = database.widgetDao()
        return dao.count(type = type)

    }


    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        do {
            val widgets = dao.exportWidgets(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (widget in widgets) {
                jsonArray.put(
                    jsonObjectOf(
                        "config" to widget.config,
                        "position" to widget.position,
                        "type" to widget.type,
                        "id" to widget.id.toString(),
                        "parentId" to widget.parentId?.toString(),
                    )
                )
            }

            val file = File(toDir, "widgets2.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (widgets.size == 100)
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeWidgets()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("widgets2.") } ?: return@withContext

        for (file in files) {
            val widgets = mutableListOf<WidgetEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = WidgetEntity(
                        type = json.getString("type"),
                        position = json.getInt("position"),
                        config = json.optString("config"),
                        id = json.getString("id").let { UUID.fromString(it) },
                        parentId = json.optString("parentId").let { if (it.isEmpty()) null else UUID.fromString(it) }
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