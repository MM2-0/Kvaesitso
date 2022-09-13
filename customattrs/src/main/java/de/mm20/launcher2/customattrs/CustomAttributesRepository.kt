package de.mm20.launcher2.customattrs

import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import java.io.File

interface CustomAttributesRepository {
    fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?>
    fun setCustomIcon(searchable: Searchable, icon: CustomIcon?)

    fun getCustomLabels(items: List<Searchable>): Flow<List<CustomLabel>>
    fun setCustomLabel(searchable: Searchable, label: String)
    fun clearCustomLabel(searchable: Searchable)

    suspend fun search(query: String): Flow<List<Searchable>>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)
}

internal class CustomAttributesRepositoryImpl(
    private val appDatabase: AppDatabase,
    private val favoritesRepository: FavoritesRepository
) : CustomAttributesRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getCustomIcon(searchable: Searchable): Flow<CustomIcon?> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            .map {
                CustomAttribute.fromDatabaseEntity(it) as? CustomIcon
            }
    }

    override fun setCustomIcon(searchable: Searchable, icon: CustomIcon?) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.clearCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            if (icon != null) {
                dao.setCustomAttribute(icon.toDatabaseEntity(searchable.key))
            }
        }
    }

    override fun getCustomLabels(items: List<Searchable>): Flow<List<CustomLabel>> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomAttributes(items.map { it.key }, CustomAttributeType.Label.value)
            .map { list ->
                list.mapNotNull { CustomAttribute.fromDatabaseEntity(it) as? CustomLabel }
            }
    }

    override fun setCustomLabel(searchable: Searchable, label: String) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            favoritesRepository.save(searchable)
            appDatabase.runInTransaction {
                dao.clearCustomAttribute(searchable.key, CustomAttributeType.Label.value)
                dao.setCustomAttribute(
                    CustomLabel(
                        key = searchable.key,
                        label = label,
                    ).toDatabaseEntity(searchable.key)
                )
            }
        }
    }

    override fun clearCustomLabel(searchable: Searchable) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.clearCustomAttribute(searchable.key, CustomAttributeType.Label.value)
        }
    }

    override suspend fun search(query: String): Flow<List<Searchable>> {
        if (query.isBlank()) {
            return flow {
                emit(emptyList())
            }
        }
        val dao = appDatabase.customAttrsDao()
        return dao.search("%$query%").map {
            favoritesRepository.getFromKeys(it)
        }
    }

    override suspend fun export(toDir: File) = withContext(Dispatchers.IO) {
        val dao = appDatabase.backupDao()
        var page = 0
        do {
            val customAttrs = dao.exportCustomAttributes(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (customAttr in customAttrs) {
                jsonArray.put(
                    jsonObjectOf(
                        "key" to customAttr.key,
                        "value" to customAttr.value,
                        "type" to customAttr.type,
                    )
                )
            }

            val file = File(toDir, "customizations.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (customAttrs.size == 100)
    }

    override suspend fun import(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = appDatabase.backupDao()
        dao.wipeCustomAttributes()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("customizations.") }
                ?: return@withContext

        for (file in files) {
            val customAttrs = mutableListOf<CustomAttributeEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    val entity = CustomAttributeEntity(
                        id = null,
                        type = json.getString("type"),
                        value = json.optString("value"),
                        key = json.optString("key"),
                    )
                    customAttrs.add(entity)
                }

                dao.importCustomAttributes(customAttrs)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }
}