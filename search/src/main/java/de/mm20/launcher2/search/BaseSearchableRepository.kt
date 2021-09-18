package de.mm20.launcher2.search

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseSearchableRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val searchQuery = SearchRepository.getInstance().currentQuery

    init {
        searchQuery.observeForever {
            onQueryChange(it)
        }
    }

    protected open fun onCancel() {

    }

    protected fun launch(
            context: CoroutineContext = EmptyCoroutineContext,
            start: CoroutineStart = CoroutineStart.DEFAULT,
            block: suspend CoroutineScope.() -> Unit
    ) {
        scope.launch(context, start, block)
    }

    private var searchJob: Job? = null
    private fun onQueryChange(query: String) {
        scope.launch {
            onCancel()
            searchJob?.takeIf { !it.isCompleted || !it.isCancelled }?.cancelAndJoin()
            searchJob = scope.launch {
                SearchRepository.getInstance().startSearch()
                search(query)
            }.also {
                it.invokeOnCompletion { SearchRepository.getInstance().endSearch() }
            }
        }
    }

    /**
     * Called when the query string changes. This method should change the current data presented
     * by this SearchableRepository.
     */
    protected abstract suspend fun search(query: String)

}