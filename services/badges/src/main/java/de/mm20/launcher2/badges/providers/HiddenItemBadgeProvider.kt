package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HiddenItemBadgeProvider(
) : BadgeProvider, KoinComponent {

    private val searchableRepository: SavableSearchableRepository by inject()

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val hiddenItemKeys = searchableRepository.getKeys(
        maxVisibility = VisibilityLevel.Hidden,
        limit = 9999,
    ).shareIn(scope, SharingStarted.WhileSubscribed(), 1)

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is SavableSearchable) return flowOf(null)
        return hiddenItemKeys.map { keys ->
            if (searchable.key in keys) {
                Badge(
                    icon = BadgeIcon(R.drawable.visibility_off_20px)
                )
            } else {
                null
            }
        }
    }
}