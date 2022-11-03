package de.mm20.launcher2.searchactions

import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import kotlinx.coroutines.flow.Flow

interface SearchActionRepository {
    fun getSearchActionBuilders(filter: TextType?): Flow<List<SearchActionBuilder>>
}

internal class SearchActionRepositoryImpl: SearchActionRepository {
    override fun getSearchActionBuilders(filter: TextType?): Flow<List<SearchActionBuilder>> {
        TODO("Not yet implemented")
    }
}