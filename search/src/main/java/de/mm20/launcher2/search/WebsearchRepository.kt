package de.mm20.launcher2.search

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Websearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface WebsearchRepository {
    fun search(query: String): Flow<List<Websearch>>

    fun getWebsearches(): Flow<List<Websearch>>

    fun insertWebsearch(websearch: Websearch)
    fun deleteWebsearch(websearch: Websearch)
}

class WebsearchRepositoryImpl(
    private val database: AppDatabase
) : WebsearchRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun search(query: String): Flow<List<Websearch>> = channelFlow {
        if (query.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }
        dataStore.data.map { it.webSearch.enabled }.collectLatest {
            if (it) {
                withContext(Dispatchers.IO) {
                    database.searchDao().getWebSearches().map {
                        it.map { Websearch(it, query) }
                    }
                }.collectLatest {
                    send(it)
                }
            } else {
                send(emptyList())
            }
        }
    }

    override fun getWebsearches(): Flow<List<Websearch>> =
        database.searchDao().getWebSearches().map {
            it.map { Websearch(it) }
        }

    override fun insertWebsearch(websearch: Websearch) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().insertWebsearch(websearch.toDatabaseEntity())
            }
        }
    }

    override fun deleteWebsearch(websearch: Websearch) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.searchDao().deleteWebsearch(websearch.toDatabaseEntity())
            }
        }
    }
}