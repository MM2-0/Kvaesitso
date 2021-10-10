package de.mm20.launcher2.search

import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class BaseSearchableRepository: KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    val searchRepository: SearchRepository by inject()
    private val searchQuery = searchRepository.currentQuery

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
                searchRepository.startSearch()
                search(query)
            }.also {
                it.invokeOnCompletion { searchRepository.endSearch() }
            }
        }
    }

    /**
     * Called when the query string changes. This method should change the current data presented
     * by this SearchableRepository.
     */
    protected abstract suspend fun search(query: String)

}