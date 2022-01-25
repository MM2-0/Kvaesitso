package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.ktx.ceilToInt
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

interface FavoritesRepository {
    fun getFavorites(): Flow<List<Searchable>>
    fun getPinnedCalendarEvents(): Flow<List<Searchable>>
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
}

internal class FavoritesRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: LauncherDataStore
) : FavoritesRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun getFavorites(): Flow<List<Searchable>> = channelFlow {

        withContext(Dispatchers.IO) {

            val gridColumns = dataStore.data.map { it.grid.columnCount }.distinctUntilChanged()
            val dao = database.searchDao()

            val pinnedFavorites = dao.getFavorites().map {
                it.mapNotNull {
                    val item = fromDatabaseEntity(it).searchable
                    if (item == null) {
                        dao.deleteByKey(it.key)
                    }
                    return@mapNotNull item
                }
            }

            pinnedFavorites.collectLatest { pinned ->
                gridColumns.collectLatest { columns ->
                    var favCount = (pinned.size.toDouble() / columns).ceilToInt() * columns
                    if (pinned.size < columns) favCount += columns
                    val autoFavs = dao.getAutoFavorites(favCount - pinned.size).mapNotNull {
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
    }

    override fun getPinnedCalendarEvents(): Flow<List<CalendarEvent>> {
        return database.searchDao().getPinnedCalendarEvents().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable as? CalendarEvent }
        }
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
                dao.insertReplaceExisting(favoritesItem.toDatabaseEntity())
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
                dao.insertReplaceExisting(favoritesItem.toDatabaseEntity())
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
                AppDatabase.getInstance(context).searchDao()
                    .incrementLaunchCount(item.toDatabaseEntity())
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
                    .saveFavorites(favorites.map { it.toDatabaseEntity() })
            }
        }
    }

    override fun getHiddenItems(): Flow<List<Searchable>> {
        return database.searchDao().getHiddenItems().map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }


    private fun fromDatabaseEntity(entity: FavoritesItemEntity): FavoritesItem {
        val deserializer: SearchableDeserializer = get { parametersOf(entity.serializedSearchable) }
        return FavoritesItem(
            key = entity.key,
            searchable = deserializer.deserialize(entity.serializedSearchable.substringAfter("#")),
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden
        )
    }
}