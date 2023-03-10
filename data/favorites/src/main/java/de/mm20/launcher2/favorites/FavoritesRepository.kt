package de.mm20.launcher2.favorites

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.WeightFactor
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONException
import org.koin.core.component.KoinComponent
import java.io.File

interface FavoritesRepository {
    /**
     * Get favorites
     * @param includeTypes Include only items of these types. Cannot be used together with excludeTypes.
     * @param excludeTypes Exclude only items of these types. Cannot be used together with includeTypes.
     * @param manuallySorted Include items that have been sorted manually
     * @param automaticallySorted Include items that are pinned but not sorted
     * @param frequentlyUsed Include items that are not pinned but most frequently used
     * @param limit Maximum number of items returned.
     */
    fun getFavorites(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        limit: Int = 100
    ): Flow<List<SavableSearchable>>


    fun getHiddenCalendarEventKeys(): Flow<List<String>>
    fun isPinned(searchable: SavableSearchable): Flow<Boolean>
    fun pinItem(searchable: SavableSearchable)
    fun unpinItem(searchable: SavableSearchable)
    fun isHidden(searchable: SavableSearchable): Flow<Boolean>
    fun hideItem(searchable: SavableSearchable)
    fun unhideItem(searchable: SavableSearchable)
    fun incrementLaunchCounter(searchable: SavableSearchable)
    fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>,
    )

    fun getHiddenItems(): Flow<List<SavableSearchable>>
    fun getHiddenItemKeys(): Flow<List<String>>

    /**
     * Returns the given keys sorted by relevance.
     * The first item in the list is the most relevant.
     * Unknown keys will not be included in the result.
     */
    fun sortByRelevance(keys: List<String>): Flow<List<String>>

    fun sortByWeight(keys: List<String>): Flow<List<String>>

    /**
     * Remove this item from the Searchable database
     */
    fun remove(searchable: SavableSearchable)

    /**
     * Remove this item from favorites and reset launch counter
     */
    fun removeFromFavorites(searchable: SavableSearchable)

    /**
     * Ensure that this searchable exists in the Favorites table.
     * If it doesn't exist, insert it with 0 launch count, not pinned and not hidden
     */
    fun save(searchable: SavableSearchable)

    /**
     * Get items with the given keys from the favorites database.
     * Items that don't exist in the database will not be returned.
     */
    suspend fun getFromKeys(keys: List<String>): List<SavableSearchable>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)

    /**
     * Remove database entries that are invalid. This includes
     * - entries that cannot be deserialized anymore
     * - entries that are inconsistent (the key column is not equal to the key of the searchable)
     */
    suspend fun cleanupDatabase(): Int
}

internal class FavoritesRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: LauncherDataStore
) : FavoritesRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getFavorites(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        manuallySorted: Boolean,
        automaticallySorted: Boolean,
        frequentlyUsed: Boolean,
        limit: Int
    ): Flow<List<SavableSearchable>> {
        val dao = database.searchDao()
        val entities = when {
            includeTypes == null && excludeTypes == null -> dao.getFavorites(
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                limit = limit
            )

            includeTypes != null && excludeTypes == null -> {
                dao.getFavoritesWithTypes(
                    includeTypes = includeTypes,
                    manuallySorted = manuallySorted,
                    automaticallySorted = automaticallySorted,
                    frequentlyUsed = frequentlyUsed,
                    limit = limit
                )
            }

            excludeTypes != null && includeTypes == null -> {
                dao.getFavoritesWithoutTypes(
                    excludeTypes = excludeTypes,
                    manuallySorted = manuallySorted,
                    automaticallySorted = automaticallySorted,
                    frequentlyUsed = frequentlyUsed,
                    limit = limit
                )
            }

            else -> throw IllegalArgumentException("You can either use includeTypes or excludeTypes, not both")
        }
        return entities.map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getHiddenCalendarEventKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenCalendarEventKeys()
    }

    override fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchDao().isPinned(searchable.key)
    }

    override fun pinItem(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = database.searchDao()
                val databaseItem = dao.getFavorite(searchable.key)
                val savedSearchable = SavedSearchable(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = databaseItem?.launchCount ?: 0,
                    pinPosition = 1,
                    hidden = false,
                    weight = databaseItem?.weight ?: 0.0
                )
                savedSearchable.toDatabaseEntity()?.let { dao.insertReplaceExisting(it) }
            }
        }
    }

    override fun unpinItem(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().unpinFavorite(searchable.key)
            }
        }
    }

    override fun isHidden(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchDao().isHidden(searchable.key)
    }

    override fun hideItem(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = database.searchDao()
                val databaseItem = dao.getFavorite(searchable.key)
                val savedSearchable = SavedSearchable(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = databaseItem?.launchCount ?: 0,
                    pinPosition = 0,
                    hidden = true,
                    weight = databaseItem?.weight ?: 0.0
                )
                savedSearchable.toDatabaseEntity()?.let { dao.insertReplaceExisting(it) }
            }
        }
    }

    override fun unhideItem(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().unhideItem(searchable.key)
            }
        }
    }

    override fun incrementLaunchCounter(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val weightFactor =
                    when (dataStore.data.map { it.resultOrdering.weightFactor }.firstOrNull()) {
                        WeightFactor.Low -> 0.1
                        WeightFactor.High -> 0.5
                        else -> 0.2
                    }
                val item = SavedSearchable(searchable.key, searchable, 0, 0, false, 0.0)
                item.toDatabaseEntity()?.let {
                    database.searchDao()
                        .incrementLaunchCount(it, weightFactor)
                }
            }
        }
    }

    override fun getHiddenItems(): Flow<List<SavableSearchable>> {
        return database.searchDao().getHiddenItems().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getHiddenItemKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenItemKeys()
    }

    override fun remove(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().deleteByKey(searchable.key)
            }
        }
    }

    override fun removeFromFavorites(searchable: SavableSearchable) {
        scope.launch {
            database.searchDao().resetPinStatusAndLaunchCounter(searchable.key)
        }
    }

    override fun save(searchable: SavableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val entity = SavedSearchable(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = 0,
                    pinPosition = 0,
                    hidden = false,
                    weight = 0.0
                ).toDatabaseEntity() ?: return@withContext
                database.searchDao().insertSkipExisting(entity)
            }
        }
    }

    override fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>
    ) {
        val dao = database.searchDao()
        scope.launch {
            withContext(Dispatchers.IO) {
                val keys = manuallySorted.map { it.key } + automaticallySorted.map { it.key }
                val entities = dao.getFromKeys(keys)
                val updatedManuallySorted = manuallySorted.mapIndexedNotNull { index, searchable ->
                    val entity = entities.find { searchable.key == it.key } ?: SavedSearchable(
                        key = searchable.key,
                        searchable = searchable,
                        launchCount = 0,
                        pinPosition = 0,
                        hidden = false,
                        weight = 0.0
                    ).toDatabaseEntity() ?: return@mapIndexedNotNull null
                    entity.pinPosition = manuallySorted.size - index + 1
                    entity
                }
                val updatedAutomaticallySorted =
                    automaticallySorted.mapIndexedNotNull { index, searchable ->
                        val entity = entities.find { searchable.key == it.key } ?: SavedSearchable(
                            key = searchable.key,
                            searchable = searchable,
                            launchCount = 0,
                            pinPosition = 0,
                            hidden = false,
                            weight = 0.0
                        ).toDatabaseEntity() ?: return@mapIndexedNotNull null
                        entity.pinPosition = 1
                        entity
                    }
                database.runInTransaction {
                    dao.unpinAll()
                    dao.insertAllReplaceExisting(updatedManuallySorted)
                    dao.insertAllReplaceExisting(updatedAutomaticallySorted)
                }
            }
        }
    }

    override fun sortByRelevance(keys: List<String>): Flow<List<String>> {
        return database.searchDao().sortByRelevance(keys)
    }

    override fun sortByWeight(keys: List<String>): Flow<List<String>> {
        return database.searchDao().sortByWeight(keys)
    }

    private fun fromDatabaseEntity(entity: SavedSearchableEntity): SavedSearchable {
        val deserializer: SearchableDeserializer =
            getDeserializer(context, entity.type)
        val searchable = deserializer.deserialize(entity.serializedSearchable)
        if (searchable == null) removeInvalidItem(entity.key)
        return SavedSearchable(
            key = entity.key,
            searchable = searchable,
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden,
            weight = entity.weight
        )
    }

    private fun removeInvalidItem(key: String) {
        scope.launch {
            database.searchDao().deleteByKey(key)
        }
    }

    override suspend fun getFromKeys(keys: List<String>): List<SavableSearchable> {
        val dao = database.searchDao()
        return dao.getFromKeys(keys)
            .mapNotNull { fromDatabaseEntity(it).searchable }
    }

    override suspend fun export(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        do {
            val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (fav in favorites) {
                jsonArray.put(
                    jsonObjectOf(
                        "key" to fav.key,
                        "type" to fav.type,
                        "hidden" to fav.hidden,
                        "launchCount" to fav.launchCount,
                        "pinPosition" to fav.pinPosition,
                        "searchable" to fav.serializedSearchable,
                        "weight" to fav.weight,
                    )
                )
            }

            val file = File(toDir, "favorites.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (favorites.size == 100)
    }

    override suspend fun import(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeFavorites()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("favorites.") } ?: return@withContext

        for (file in files) {
            val favorites = mutableListOf<SavedSearchableEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = SavedSearchableEntity(
                        key = json.getString("key"),
                        type = json.optString("type").takeIf { it.isNotEmpty() } ?: continue,
                        serializedSearchable = json.getString("searchable"),
                        launchCount = json.getInt("launchCount"),
                        hidden = json.getBoolean("hidden"),
                        pinPosition = json.getInt("pinPosition"),
                        weight = json.optDouble("weight").takeIf { !it.isNaN() } ?: 0.0
                    )
                    favorites.add(entity)
                }

                dao.importFavorites(favorites)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }

    override suspend fun cleanupDatabase(): Int {
        var removed = 0
        val job = scope.launch {
            val dao = database.backupDao()
            var page = 0
            do {
                val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
                for (fav in favorites) {
                    val item = fromDatabaseEntity(fav)
                    if (item.searchable == null || item.searchable.key != item.key) {
                        removeInvalidItem(item.key)
                        removed++
                        Log.i(
                            "MM20",
                            "SearchableDatabase cleanup: removed invalid item ${item.key}"
                        )
                    }
                }
                page++
            } while (favorites.size == 100)
        }
        job.join()
        return removed
    }
}