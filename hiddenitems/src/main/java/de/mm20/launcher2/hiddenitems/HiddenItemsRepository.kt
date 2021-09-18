package de.mm20.launcher2.hiddenitems

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Searchable

/**
 * A low level repository for hidden items. This can only be used to retrieve keys and to check
 * whether an item is hidden. To retrieve actual Searchable objects, use FavoritesRepository.
 */
class HiddenItemsRepository private constructor(val context: Context) {

    val hiddenItemsKeys : LiveData<List<String>> = AppDatabase.getInstance(context).searchDao().getHiddenItemKeys()

    fun isHidden(item: Searchable): LiveData<Boolean> {
        return AppDatabase.getInstance(context).searchDao().isHidden(item.key)
    }

    companion object {
        private lateinit var instance: HiddenItemsRepository

        fun getInstance(context: Context): HiddenItemsRepository {
            if(!Companion::instance.isInitialized) instance = HiddenItemsRepository(context.applicationContext)
            return instance
        }
    }
}