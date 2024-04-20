package de.mm20.launcher2.search

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

interface QueryableRepository<TQuery, TSearchable : Searchable> {
    fun search(query: TQuery, allowNetwork: Boolean): Flow<ImmutableList<TSearchable>>
}

interface SearchableRepository<T : Searchable> : QueryableRepository<String, T> {
    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<T>>
}