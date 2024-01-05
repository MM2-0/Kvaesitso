package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent

class SuspendedAppsBadgeProvider : BadgeProvider, KoinComponent {

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        return if (searchable is Application && searchable.isSuspended) {
            flowOf(MutableBadge(iconRes = R.drawable.ic_badge_suspended))
        } else {
            flowOf(null)
        }
    }
}