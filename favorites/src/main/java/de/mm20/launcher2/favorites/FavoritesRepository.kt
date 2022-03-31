package de.mm20.launcher2.favorites

import android.content.Context
import de.mm20.launcher2.appshortcuts.AppShortcutDeserializer
import de.mm20.launcher2.appshortcuts.AppShortcutSerializer
import de.mm20.launcher2.calendar.CalendarEventDeserializer
import de.mm20.launcher2.calendar.CalendarEventSerializer
import de.mm20.launcher2.contacts.ContactDeserializer
import de.mm20.launcher2.contacts.ContactSerializer
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.files.*
import de.mm20.launcher2.ktx.ceilToInt
import de.mm20.launcher2.search.NullDeserializer
import de.mm20.launcher2.search.NullSerializer
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.*
import de.mm20.launcher2.websites.WebsiteDeserializer
import de.mm20.launcher2.websites.WebsiteSerializer
import de.mm20.launcher2.wikipedia.WikipediaDeserializer
import de.mm20.launcher2.wikipedia.WikipediaSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

interface FavoritesRepository {
    fun getFavorites(
        columns: Int,
        maxRows: Int? = null,
        excludeCalendarEvents: Boolean = false
    ): Flow<List<Searchable>>

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


    private fun fromDatabaseEntity(entity: FavoritesItemEntity): FavoritesItem {
        val deserializer: SearchableDeserializer = getDeserializer(context, entity.serializedSearchable)
        return FavoritesItem(
            key = entity.key,
            searchable = deserializer.deserialize(entity.serializedSearchable.substringAfter("#")),
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden
        )
    }
}