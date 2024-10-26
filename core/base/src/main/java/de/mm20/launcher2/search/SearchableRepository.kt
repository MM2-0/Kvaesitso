package de.mm20.launcher2.search

import kotlinx.coroutines.flow.Flow

interface SearchableRepository<T : Searchable> {
    fun search(query: String, allowNetwork: Boolean): Flow<List<T>>
}