package de.mm20.launcher2.favorites

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.data.CalendarEvent
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
    ): Flow<List<PinnableSearchable>>


    fun getPinnedCalendarEvents(): Flow<List<PinnableSearchable>>
    fun getHiddenCalendarEventKeys(): Flow<List<String>>
    fun isPinned(searchable: PinnableSearchable): Flow<Boolean>
    fun pinItem(searchable: PinnableSearchable)
    fun unpinItem(searchable: PinnableSearchable)
    fun isHidden(searchable: PinnableSearchable): Flow<Boolean>
    fun hideItem(searchable: PinnableSearchable)
    fun unhideItem(searchable: PinnableSearchable)
    fun incrementLaunchCounter(searchable: PinnableSearchable)
    fun updateFavorites(
        manuallySorted: List<PinnableSearchable>,
        automaticallySorted: List<PinnableSearchable>,
    )

    fun getHiddenItems(): Flow<List<PinnableSearchable>>
    fun getHiddenItemKeys(): Flow<List<String>>

    /**
     * Remove this item from the Searchable database
     */
    fun remove(searchable: PinnableSearchable)

    /**
     * Remove this item from favorites and reset launch counter
     */
    fun removeFromFavorites(searchable: PinnableSearchable)

    /**
     * Ensure that this searchable exists in the Favorites table.
     * If it doesn't exist, insert it with 0 launch count, not pinned and not hidden
     */
    fun save(searchable: PinnableSearchable)

    /**
     * Get items with the given keys from the favorites database.
     * Items that don't exist in the database will not be returned.
     */
    suspend fun getFromKeys(keys: List<String>): List<PinnableSearchable>

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
) : FavoritesRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getFavorites(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        manuallySorted: Boolean,
        automaticallySorted: Boolean,
        frequentlyUsed: Boolean,
        limit: Int
    ): Flow<List<PinnableSearchable>> {
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

    override fun getPinnedCalendarEvents(): Flow<List<CalendarEvent>> {
        return database.searchDao().getFavoritesWithTypes(
            includeTypes = listOf("calendar"),
            automaticallySorted = true,
            manuallySorted = true,
            limit = 50
        ).map {
            it.mapNotNull { fromDatabaseEntity(it).searchable as? CalendarEvent }
        }
    }

    override fun getHiddenCalendarEventKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenCalendarEventKeys()
    }

    override fun isPinned(searchable: PinnableSearchable): Flow<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isPinned(searchable.key)
    }

    override fun pinItem(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(context).searchDao()
                val databaseItem = dao.getFavorite(searchable.key)
                val favoritesItem = FavoritesItem(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = databaseItem?.launchCount ?: 0,
                    pinPosition = 1,
                    hidden = false
                )
                favoritesItem.toDatabaseEntity()?.let { dao.insertReplaceExisting(it) }
            }
        }
    }

    override fun unpinItem(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unpinFavorite(searchable.key)
            }
        }
    }

    override fun isHidden(searchable: PinnableSearchable): Flow<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isHidden(searchable.key)
    }

    override fun hideItem(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(context).searchDao()
                val databaseItem = dao.getFavorite(searchable.key)
                val favoritesItem = FavoritesItem(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = databaseItem?.launchCount ?: 0,
                    pinPosition = 0,
                    hidden = true
                )
                favoritesItem.toDatabaseEntity()?.let { dao.insertReplaceExisting(it) }
            }
        }
    }

    override fun unhideItem(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unhideItem(searchable.key)
            }
        }
    }

    override fun incrementLaunchCounter(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val item = FavoritesItem(searchable.key, searchable, 0, 0, false)
                item.toDatabaseEntity()?.let {
                    AppDatabase.getInstance(context).searchDao()
                        .incrementLaunchCount(it)
                }
            }
        }
    }

    override fun getHiddenItems(): Flow<List<PinnableSearchable>> {
        return database.searchDao().getHiddenItems().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getHiddenItemKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenItemKeys()
    }

    override fun remove(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().deleteByKey(searchable.key)
            }
        }
    }

    override fun removeFromFavorites(searchable: PinnableSearchable) {
        scope.launch {
            database.searchDao().resetPinStatusAndLaunchCounter(searchable.key)
        }
    }

    override fun save(searchable: PinnableSearchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val entity = FavoritesItem(
                    key = searchable.key,
                    searchable = searchable,
                    launchCount = 0,
                    pinPosition = 0,
                    hidden = false,
                ).toDatabaseEntity() ?: return@withContext
                database.searchDao().insertSkipExisting(entity)
            }
        }
    }

    override fun updateFavorites(
        manuallySorted: List<PinnableSearchable>,
        automaticallySorted: List<PinnableSearchable>
    ) {
        val dao = database.searchDao()
        scope.launch {
            withContext(Dispatchers.IO) {
                val keys = manuallySorted.map { it.key } + automaticallySorted.map { it.key }
                val entities = dao.getFromKeys(keys)
                val updatedManuallySorted = manuallySorted.mapIndexedNotNull { index, searchable ->
                    val entity = entities.find { searchable.key == it.key } ?: FavoritesItem(
                        key = searchable.key,
                        searchable = searchable,
                        launchCount = 0,
                        pinPosition = 0,
                        hidden = false,
                    ).toDatabaseEntity() ?: return@mapIndexedNotNull null
                    entity.pinPosition = manuallySorted.size - index + 1
                    entity
                }
                val updatedAutomaticallySorted =
                    automaticallySorted.mapIndexedNotNull { index, searchable ->
                        val entity = entities.find { searchable.key == it.key } ?: FavoritesItem(
                            key = searchable.key,
                            searchable = searchable,
                            launchCount = 0,
                            pinPosition = 0,
                            hidden = false,
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


    private fun fromDatabaseEntity(entity: FavoritesItemEntity): FavoritesItem {
        val deserializer: SearchableDeserializer =
            getDeserializer(context, entity.serializedSearchable)
        val searchable = deserializer.deserialize(entity.serializedSearchable.substringAfter("#"))
        if (searchable == null) removeInvalidItem(entity.key)
        return FavoritesItem(
            key = entity.key,
            searchable = searchable,
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden
        )
    }

    private fun removeInvalidItem(key: String) {
        scope.launch {
            database.searchDao().deleteByKey(key)
        }
    }

    override suspend fun getFromKeys(keys: List<String>): List<PinnableSearchable> {
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
                        "hidden" to fav.hidden,
                        "launchCount" to fav.launchCount,
                        "pinPosition" to fav.pinPosition,
                        "searchable" to fav.serializedSearchable
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
            val favorites = mutableListOf<FavoritesItemEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = FavoritesItemEntity(
                        key = json.getString("key"),
                        serializedSearchable = json.getString("searchable"),
                        launchCount = json.getInt("launchCount"),
                        hidden = json.getBoolean("hidden"),
                        pinPosition = json.getInt("pinPosition")
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