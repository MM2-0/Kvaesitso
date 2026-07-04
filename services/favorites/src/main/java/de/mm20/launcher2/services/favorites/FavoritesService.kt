package de.mm20.launcher2.services.favorites

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.PinnedLevel
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.flow.Flow

class FavoritesService(
    private val searchableRepository: SavableSearchableRepository,
) {
    fun getFavorites(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.FrequentlyUsed,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        limit: Int = 100,
    ): Flow<List<SavableSearchable>> {
        return searchableRepository.get(
            includeTypes = includeTypes,
            excludeTypes = excludeTypes,
            minPinnedLevel = minPinnedLevel,
            maxPinnedLevel = maxPinnedLevel,
            limit = limit,
            minVisibility = VisibilityLevel.SearchOnly,
        )
    }

    fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return searchableRepository.isPinned(searchable)
    }

    fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel> {
        return searchableRepository.getVisibility(searchable)
    }

    fun pinItem(searchable: SavableSearchable) {
        searchableRepository.upsert(
            searchable,
            pinned = true,
        )
    }

    fun reset(searchable: SavableSearchable) {
        searchableRepository.update(
            searchable,
            pinned = false,
            visibility = VisibilityLevel.Default,
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

    fun setVisibility(searchable: SavableSearchable, visibility: VisibilityLevel) {
        searchableRepository.upsert(
            searchable,
            visibility = visibility,
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

    fun upsert(searchable: SavableSearchable) {
        searchableRepository.upsert(searchable)
    }
}