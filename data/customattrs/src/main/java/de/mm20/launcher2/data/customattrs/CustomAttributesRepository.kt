package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import java.io.File

interface CustomAttributesRepository: Backupable {

    fun search(query: String): Flow<ImmutableList<SavableSearchable>>

    fun getCustomIcon(searchable: SavableSearchable): Flow<CustomIcon?>
    fun setCustomIcon(searchable: SavableSearchable, icon: CustomIcon?)

    fun getCustomLabels(items: List<SavableSearchable>): Flow<List<CustomLabel>>
    fun setCustomLabel(searchable: SavableSearchable, label: String)
    fun clearCustomLabel(searchable: SavableSearchable)

    fun setTags(searchable: SavableSearchable, tags: List<String>)
    fun getTags(searchable: SavableSearchable): Flow<List<String>>

    fun getAllTags(startsWith: String? = null): Flow<List<String>>
    fun getItemsForTag(tag: String): Flow<List<SavableSearchable>>
    fun setItemsForTag(tag: String, items: List<SavableSearchable>): Job
    fun addTag(item: SavableSearchable, tag: String)

    fun renameTag(oldName: String, newName: String): Job
    fun deleteTag(tag: String): Job
    suspend fun cleanupDatabase(): Int
}

internal class CustomAttributesRepositoryImpl(
    private val appDatabase: AppDatabase,
    private val searchableRepository: SavableSearchableRepository
) : CustomAttributesRepository {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getCustomIcon(searchable: SavableSearchable): Flow<CustomIcon?> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            .map {
                CustomAttribute.fromDatabaseEntity(it) as? CustomIcon
            }
    }

    override fun setCustomIcon(searchable: SavableSearchable, icon: CustomIcon?) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.clearCustomAttribute(searchable.key, CustomAttributeType.Icon.value)
            if (icon != null) {
                dao.setCustomAttribute(icon.toDatabaseEntity(searchable.key))
            }
        }
    }

    override fun getCustomLabels(items: List<SavableSearchable>): Flow<List<CustomLabel>> {
        if (items.size <= 999) {
            val dao = appDatabase.customAttrsDao()
            return dao.getCustomAttributes(items.map { it.key }, CustomAttributeType.Label.value)
                .map { list ->
                    list.mapNotNull { CustomAttribute.fromDatabaseEntity(it) as? CustomLabel }
                }
        } else {
            val dao = appDatabase.customAttrsDao()
            return combine(items.chunked(999).map { chunk ->
                dao.getCustomAttributes(chunk.map { it.key }, CustomAttributeType.Label.value)
            }) { results ->
                results.flatMap { list ->
                    list.mapNotNull { CustomAttribute.fromDatabaseEntity(it) as? CustomLabel }
                }
            }
        }
    }

    override fun setCustomLabel(searchable: SavableSearchable, label: String) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            searchableRepository.insert(searchable)
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

    override fun clearCustomLabel(searchable: SavableSearchable) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.clearCustomAttribute(searchable.key, CustomAttributeType.Label.value)
        }
    }

    override fun setTags(searchable: SavableSearchable, tags: List<String>) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            searchableRepository.insert(searchable)
            dao.setTags(searchable.key, tags.map {
                CustomTag(it).toDatabaseEntity(searchable.key)
            })
        }
    }

    override fun getTags(searchable: SavableSearchable): Flow<List<String>> {
        val dao = appDatabase.customAttrsDao()
        return dao.getCustomAttributes(listOf(searchable.key), CustomAttributeType.Tag.value).map {
            it.map { it.value }
        }
    }

    override fun getAllTags(startsWith: String?): Flow<List<String>> {
        val dao = appDatabase.customAttrsDao()
        return if (startsWith != null) {
            dao.getAllTagsLike("$startsWith%")
        } else {
            dao.getAllTags()
        }
    }

    override fun getItemsForTag(tag: String): Flow<List<SavableSearchable>> {
        val dao = appDatabase.customAttrsDao()
        return dao.getItemsWithTag(tag).flatMapLatest {
            searchableRepository.getByKeys(it)
        }
    }

    override fun setItemsForTag(tag: String, items: List<SavableSearchable>): Job {
        val dao = appDatabase.customAttrsDao()
        return scope.launch {
            dao.setItemsWithTag(tag, items.map { it.key })
            for (item in items) {
                searchableRepository.insert(item)
            }
        }
    }


    override fun addTag(item: SavableSearchable, tag: String) {
        val dao = appDatabase.customAttrsDao()
        scope.launch {
            dao.addTag(item.key, tag)
        }
    }

    override fun renameTag(oldName: String, newName: String): Job {
        val dao = appDatabase.customAttrsDao()
        return scope.launch {
            dao.renameTag(oldName, newName)
        }
    }

    override fun deleteTag(tag: String): Job {
        val dao = appDatabase.customAttrsDao()
        return scope.launch {
            dao.deleteTag(tag)
        }
    }

    override fun search(query: String): Flow<ImmutableList<SavableSearchable>> {
        if (query.isBlank()) {
            return flow {
                emit(persistentListOf())
            }
        }
        val dao = appDatabase.customAttrsDao()
        return dao.search("%$query%").flatMapLatest {
            searchableRepository.getByKeys(it).map {
                it.toImmutableList()
            }
        }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
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

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
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

    override suspend fun cleanupDatabase(): Int {
        val dao = appDatabase.backupDao()
        var removed = 0
        val job = scope.launch {
            removed = dao.cleanUp()
        }
        job.join()
        return removed
    }
}