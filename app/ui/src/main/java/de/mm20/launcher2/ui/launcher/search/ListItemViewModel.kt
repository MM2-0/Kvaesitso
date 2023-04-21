package de.mm20.launcher2.ui.launcher.search

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ListItemViewModelStore() {
    private val cache = ViewModelCache()

    operator fun <T: ListItemViewModel>get(key: String, modelClass: Class<T>): T {
        val cachedInstance = cache[key]
        if (cachedInstance != null) {
            return cachedInstance as T
        }
        val newInstance = modelClass.getDeclaredConstructor().newInstance()
        cache.put(key, newInstance)
        return newInstance
    }

    inline operator fun <reified T: ListItemViewModel>get(key: String): T {
        return get(key, T::class.java)
    }

    fun clear() {
        cache.evictAll()
    }
}

private class ViewModelCache: LruCache<String, ListItemViewModel>(500) {
    override fun entryRemoved(
        evicted: Boolean,
        key: String?,
        oldValue: ListItemViewModel?,
        newValue: ListItemViewModel?
    ) {
        super.entryRemoved(evicted, key, oldValue, newValue)
        oldValue?.clear()
    }
}

/**
 * Knock-off of Android's ViewModel class but not tied to a lifecycle.
 * This is useful for view models that are not tied to a lifecycle, e.g. for list items.
 */
open class ListItemViewModel {
    val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun clear() {
        viewModelScope.coroutineContext.cancel()
        onCleared()
    }
    protected open fun onCleared() {

    }
}

val LocalListItemViewModelStore = staticCompositionLocalOf { ListItemViewModelStore() }

@Composable
inline fun <reified T: ListItemViewModel>listItemViewModel(key: String): T {
    val store = LocalListItemViewModelStore.current
    return remember(key) {
        store[key]
    }
}