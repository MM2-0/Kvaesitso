package de.mm20.launcher2.favorites

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.FavoritesItemEntity
import de.mm20.launcher2.ktx.ceilToInt
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.math.max
import kotlin.math.min

class FavoritesRepository(private val context: Context) : BaseSearchableRepository() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val favorites = MediatorLiveData<List<Searchable>>()
    private val favoriteItems: LiveData<List<FavoritesItemEntity>> = MutableLiveData()

    val hiddenItems = MediatorLiveData<List<Searchable>>()

    private val pinnedFavorites = AppDatabase.getInstance(context).searchDao().getFavorites()


    val pinnedCalendarEvents = MediatorLiveData<List<CalendarEvent>>()

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

    private val reloadFavorites: (String) -> Unit = {
        scope.launch {
            if(!LauncherPreferences.instance.searchShowFavorites) {
                favorites.value = emptyList()
                return@launch
            }
            val favs = mutableListOf<Searchable>()
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(context).searchDao()
                val favItems = pinnedFavorites.value ?: emptyList()
                favs.addAll(favItems.mapNotNull {
                    val item = fromDatabaseEntity(it)
                    if (item.searchable == null) {
                        dao.deleteByKey(item.key)
                    }
                    if (item.searchable is CalendarEvent) return@mapNotNull null
                    item.searchable
                })
                var favCount = (favs.size.toDouble() / columns).ceilToInt() * columns
                if(favItems.size < columns) favCount += columns
                val autoFavs = dao.getAutoFavorites(favCount - favs.size)
                favs.addAll(autoFavs.mapNotNull {
                    val item = fromDatabaseEntity(it)
                    if (item.searchable == null) {
                        dao.deleteByKey(item.key)
                    }
                    item.searchable
                })
            }
            favorites.value = favs
        }
    }

    private var columns = 1

    init {
        val hidden = AppDatabase.getInstance(context).searchDao().getHiddenItems()
        hiddenItems.addSource(hidden) { h ->
            hiddenItems.value = h.mapNotNull { fromDatabaseEntity(it).searchable }
        }
        favorites.addSource(pinnedFavorites) {
            reloadFavorites("")
        }
        pinnedCalendarEvents.addSource(pinnedFavorites) {
            scope.launch {
                val dao = AppDatabase.getInstance(context).searchDao()
                pinnedCalendarEvents.value = it.filter { it.key.startsWith("calendar://") }.mapNotNull {
                    val item = fromDatabaseEntity(it)
                    if (item.searchable == null) {
                        withContext(Dispatchers.IO) { dao.deleteByKey(item.key) }
                    }
                    item.searchable as? CalendarEvent
                }
            }
        }
        LauncherPreferences.instance.doOnPreferenceChange(
                "search_show_favorites",
                "search_auto_add_favorites",
                action = reloadFavorites
        )
    }


    fun isHidden(searchable: Searchable): LiveData<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isHidden(searchable.key)
    }

    fun getFavorites(columns: Int): LiveData<List<Searchable>> {
        if (columns != this.columns) {
            this.columns = columns
            reloadFavorites("")
        }
        return favorites
    }

    override suspend fun search(query: String) {
        if (query.isEmpty()) {
            reloadFavorites("")
        } else {
            favorites.value = emptyList()
        }
    }

    fun isPinned(searchable: Searchable): LiveData<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isPinned(searchable.key)
    }

    fun pinItem(searchable: Searchable) {
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

    fun unpinItem(searchable: Searchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unpinFavorite(searchable.key)
            }
        }
    }

    fun hideItem(searchable: Searchable) {
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

    fun unhideItem(searchable: Searchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().unhideItem(searchable.key)
            }
        }
    }

    fun deleteItem(key: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().deleteByKey(key)
            }
        }
    }

    fun incrementLaunchCount(searchable: Searchable) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val item = FavoritesItem(searchable.key, searchable, 0, 0, false)
                AppDatabase.getInstance(context).searchDao().incrementLaunchCount(item.toDatabaseEntity())
            }
        }
    }

    suspend fun getAllFavoriteItems(): List<FavoritesItem> {
        return withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).searchDao().getAllFavoriteItems().mapNotNull {
                fromDatabaseEntity(it).takeIf { it.searchable != null }
            }
        }
    }

    fun saveFavorites(favorites: MutableList<FavoritesItem>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().saveFavorites(favorites.map { it.toDatabaseEntity() })
            }
        }
    }

    fun getTopFavorites(count: Int): LiveData<List<Searchable>> {
        val favs = MediatorLiveData<List<Searchable>>()
        favs.addSource(favorites) {
            favs.value = it.subList(0, min(count, it.size))
        }
        return favs
    }

}