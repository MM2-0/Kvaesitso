package de.mm20.launcher2.search

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Websearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WebsearchRepository private constructor(val context: Context) : BaseSearchableRepository() {

    val websearches = MutableLiveData<List<Websearch>>(emptyList())

    val allWebsearches = MediatorLiveData<List<Websearch>>()

    init {
        val databaseWebsearches = AppDatabase.getInstance(context).searchDao().getWebSearchesLiveData()
        allWebsearches.addSource(databaseWebsearches) {
            allWebsearches.value = it.map { Websearch(it) }
        }
    }

    override suspend fun search(query: String) {
        if (query.isEmpty()) {
            websearches.value = emptyList()
            return
        }
        val searches = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).searchDao().getWebSearches().map {
                Websearch(it, query)
            }
        }
        websearches.value = searches
    }

    fun insertWebsearch(websearch: Websearch) {
        launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().insertWebsearch(websearch.toDatabaseEntity())
            }
        }
    }

    fun deleteWebsearch(websearch: Websearch) {
        launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).searchDao().deleteWebsearch(websearch.toDatabaseEntity())
            }
        }
    }

    companion object {
        private lateinit var instance: WebsearchRepository

        fun getInstance(context: Context): WebsearchRepository {
            if (!::instance.isInitialized) instance = WebsearchRepository(context.applicationContext)
            return instance
        }
    }
}