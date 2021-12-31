package de.mm20.launcher2.search

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.data.Websearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface WebsearchRepository {
    fun search(query: String): Flow<List<Websearch>>

    fun getWebsearches(): Flow<List<Websearch>>

    fun insertWebsearch(websearch: Websearch)
    fun deleteWebsearch(websearch: Websearch)
}

class WebsearchRepositoryImpl(
    private val database: AppDatabase
) : WebsearchRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun search(query: String): Flow<List<Websearch>> = channelFlow {
        if (query.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }
        withContext(Dispatchers.IO) {
            database.searchDao().getWebSearches().map {
                it.map { Websearch(it, query) }
            }
        }.collectLatest {
            send(it)
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