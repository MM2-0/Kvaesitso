package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.ktx.ceilToInt
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.koin.core.component.KoinComponent
import java.io.File

interface FavoritesRepository {
    fun getFavorites(
        columns: Int,
        maxRows: Int? = null,
        excludeCalendarEvents: Boolean = false
    ): Flow<List<Searchable>>

    fun getPinnedCalendarEvents(): Flow<List<Searchable>>
    fun getHiddenCalendarEventKeys(): Flow<List<String>>
    fun isPinned(searchable: Searchable): Flow<Boolean>
    fun pinItem(searchable: Searchable)
    fun unpinItem(searchable: Searchable)
    fun isHidden(searchable: Searchable): Flow<Boolean>
    fun hideItem(searchable: Searchable)
    fun unhideItem(searchable: Searchable)
    fun incrementLaunchCounter(searchable: Searchable)
    suspend fun getAllFavoriteItems(): List<FavoritesItem>
    fun saveFavorites(favorites: List<FavoritesItem>)
    fun getHiddenItems(): Flow<List<Searchable>>
    fun getHiddenItemKeys(): Flow<List<String>>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)
}

internal class FavoritesRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
) : FavoritesRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getFavorites(
        columns: Int,
        maxRows: Int?,
        excludeCalendarEvents: Boolean
    ): Flow<List<Searchable>> =
        channelFlow {
            withContext(Dispatchers.IO) {
                val dao = database.searchDao()
                val pinnedFavorites =
                    dao.getFavorites(excludeCalendarEvents, columns * (maxRows ?: 20)).map {
                        it.mapNotNull {
                            val item = fromDatabaseEntity(it).searchable
                            if (item == null) {
                                dao.deleteByKey(it.key)
                            }
                            return@mapNotNull item
                        }
                    }

                pinnedFavorites.collectLatest { pinned ->
                    var favCount = (pinned.size.toDouble() / columns).ceilToInt() * columns
                    if (pinned.size < columns) favCount += columns
                    val autoFavs = dao.getAutoFavorites(
                        favCount.coerceAtMost((maxRows ?: 20) * columns) - pinned.size
                    ).mapNotNull {
                        val item = fromDatabaseEntity(it).searchable
                        if (item == null) {
                            dao.deleteByKey(it.key)
                        }
                        return@mapNotNull item
                    }
                    send(pinned + autoFavs)
                }
            }
        }

    override fun getPinnedCalendarEvents(): Flow<List<CalendarEvent>> {
        return database.searchDao().getPinnedCalendarEvents().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable as? CalendarEvent }
        }
    }

    override fun getHiddenCalendarEventKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenCalendarEventKeys()
    }

    override fun isPinned(searchable: Searchable): Flow<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isPinned(searchable.key)
    }

    override fun pinItem(searchable: Searchable) {
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

    override fun unpinItem(searchable: Searchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unpinFavorite(searchable.key)
            }
        }
    }

    override fun isHidden(searchable: Searchable): Flow<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isHidden(searchable.key)
    }

    override fun hideItem(searchable: Searchable) {
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

    override fun unhideItem(searchable: Searchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unhideItem(searchable.key)
            }
        }
    }

    override fun incrementLaunchCounter(searchable: Searchable) {
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

    override suspend fun getAllFavoriteItems(): List<FavoritesItem> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).searchDao().getAllFavoriteItems().mapNotNull {
                fromDatabaseEntity(it).takeIf { it.searchable != null }
            }
        }
    }

    override fun saveFavorites(favorites: List<FavoritesItem>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao()
                    .saveFavorites(favorites.mapNotNull { it.toDatabaseEntity() })
            }
        }
    }

    override fun getHiddenItems(): Flow<List<Searchable>> {
        return database.searchDao().getHiddenItems().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getHiddenItemKeys(): Flow<List<String>> {
        return database.searchDao().getHiddenItemKeys()
    }


    private fun fromDatabaseEntity(entity: FavoritesItemEntity): FavoritesItem {
        val deserializer: SearchableDeserializer =
            getDeserializer(context, entity.serializedSearchable)
        return FavoritesItem(
            key = entity.key,
            searchable = deserializer.deserialize(entity.serializedSearchable.substringAfter("#")),
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden
        )
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

        val files = fromDir.listFiles { _, name -> name.startsWith("favorites.") } ?: return@withContext

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
}