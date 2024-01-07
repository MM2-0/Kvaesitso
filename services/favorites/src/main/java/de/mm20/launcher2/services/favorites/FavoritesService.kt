package de.mm20.launcher2.services.favorites

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.flow.Flow

class FavoritesService(
    private val searchableRepository: SavableSearchableRepository,
) {
    fun getFavorites(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        limit: Int = 100,
    ): Flow<List<SavableSearchable>> {
        return searchableRepository.get(
            hidden = false,
            includeTypes = includeTypes,
            excludeTypes = excludeTypes,
            manuallySorted = manuallySorted,
            automaticallySorted = automaticallySorted,
            frequentlyUsed = frequentlyUsed,
            limit = limit,
        )
    }

    fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return searchableRepository.isPinned(searchable)
    }

    fun isHidden(searchable: SavableSearchable): Flow<Boolean> {
        return searchableRepository.isHidden(searchable)
    }

    fun pinItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            pinned = true,
            hidden = false,
        )
    }

    fun reset(searchable: SavableSearchable) {
        searchableRepository.update(
            searchable,
            pinned = false,
            hidden = false,
            weight = 0.0,
            launchCount = 0,
        )
    }

    fun unpinItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            pinned = false,
        )
    }

    fun hideItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            hidden = true,
            pinned = false,
        )
    }

    fun unhideItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            hidden = false,
        )
    }

    fun reportLaunch(searchable: SavableSearchable) {
        searchableRepository.touch(searchable)
    }

    fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>
    ) {
        searchableRepository.updateFavorites(
            manuallySorted = manuallySorted,
            automaticallySorted = automaticallySorted
        )
    }

    fun delete(searchable: SavableSearchable) {
        searchableRepository.delete(searchable)
    }
}