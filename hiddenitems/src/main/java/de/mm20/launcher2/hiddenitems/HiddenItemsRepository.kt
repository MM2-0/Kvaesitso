package de.mm20.launcher2.hiddenitems

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A low level repository for hidden items. This can only be used to retrieve keys and to check
 * whether an item is hidden. To retrieve actual Searchable objects, use FavoritesRepository.
 */
class HiddenItemsRepository(val context: Context, database: AppDatabase) {

    val scope = CoroutineScope(Job() + Dispatchers.Default)
    val hiddenItemsKeys = MutableStateFlow<List<String>>(emptyList())

    init {
        scope.launch {
            AppDatabase.getInstance(context).searchDao().getHiddenItemKeys().collectLatest {
                hiddenItemsKeys.value = it
            }
        }
    }


    fun isHidden(item: Searchable): Flow<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isHidden(item.key)
    }
}