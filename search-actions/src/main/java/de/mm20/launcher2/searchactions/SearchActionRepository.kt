package de.mm20.launcher2.searchactions

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SearchActionEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.UUID

interface SearchActionRepository {
    fun getSearchActionBuilders(): Flow<List<SearchActionBuilder>>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)
}

internal class SearchActionRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase
): SearchActionRepository {
    override fun getSearchActionBuilders(): Flow<List<SearchActionBuilder>> {
        val dao = database.searchActionDao()
        return dao.getSearchActions().map { it.mapNotNull { SearchActionBuilder.from(it) } }
    }

    override suspend fun export(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        var iconCounter = 0
        do {
            val websearches = dao.exportSearchActions(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (websearch in websearches) {
                var customIcon = websearch.customIcon
                if (customIcon != null) {
                    val fileName = "asset.searchaction.${iconCounter.toString().padStart(4, '0')}"
                    val iconAssetFile = File(toDir, fileName)
                    File(customIcon).inputStream().use { inStream ->
                        iconAssetFile.outputStream().use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    customIcon = fileName

                    iconCounter++
                }
                jsonArray.put(
                    jsonObjectOf(
                        "color" to websearch.color,
                        "label" to websearch.label,
                        "data" to websearch.data,
                        "icon" to websearch.icon,
                        "customIcon" to customIcon,
                        "options" to websearch.options,
                        "position" to websearch.position,
                        "type" to websearch.type,
                    )
                )
            }

            val file = File(toDir, "searchactions.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (websearches.size == 100)
    }

    override suspend fun import(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeSearchActions()

        val files = fromDir.listFiles { _, name -> name.startsWith("searchactions.") } ?: return@withContext

        for (file in files) {
            val searchActions = mutableListOf<SearchActionEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    val customIcon = json.optString("customIcon").takeIf { it.isNotEmpty() }

                    var iconFile: File? = null

                    if (customIcon != null) {
                        val asset = File(fromDir, customIcon)
                        iconFile = File(context.filesDir, UUID.randomUUID().toString())
                        asset.inputStream().use { inStream ->
                            iconFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }

                    val entity = SearchActionEntity(
                        position = json.getInt("position"),
                        data = json.getString("data"),
                        color = json.optInt("color", 0),
                        label = json.getString("label"),
                        icon = json.optInt("icon", 0),
                        customIcon = iconFile?.absolutePath,
                        options = json.optString("options").takeIf { it.isNotEmpty() },
                        type = json.getString("type"),
                    )
                    searchActions.add(entity)
                }

                dao.importSearchActions(searchActions)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }
}