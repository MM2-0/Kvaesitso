package de.mm20.launcher2.icons

import android.content.Context
import android.util.Log
import android.util.LruCache
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IconRepository private constructor(val context: Context) {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val cache = LruCache<String, LauncherIcon>(200)

    fun getIcon(searchable: Searchable, size: Int): Flow<LauncherIcon> = flow {
        var icon = cache.get(searchable.key)
        if (icon != null) {
            emit(icon)
            return@flow
        }
        val placeholderIcon = withContext(Dispatchers.IO) {
            searchable.getPlaceholderIcon(context)
        }
        emit(placeholderIcon)
        icon = withContext(Dispatchers.IO) {
            searchable.loadIconAsync(context, size)
        }
        if (icon != null) {
            cache.put(searchable.key, icon)
            emit(icon)
        }
    }

    /**
     * Returns the icon for the given Searchable if it was requested earlier and is still in cache.
     * Returns `null` otherwise.
     */
    fun getIconIfCached(searchable: Searchable): LauncherIcon? {
        return cache[searchable.key]
    }

    fun requestIconPackListUpdate() {
        scope.launch {
            IconPackManager.getInstance(context).updateIconPacks()
        }
    }

    fun removeIconFromCache(searchable: Searchable) {
        cache.remove(searchable.key)
    }

    fun clearCache() {
        cache.evictAll()
    }


    companion object {
        private lateinit var instance: IconRepository

        fun getInstance(context: Context): IconRepository {
            if (!::instance.isInitialized) instance = IconRepository(context.applicationContext)
            return instance
        }
    }
}