package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeIcon
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent

class SuspendedAppsBadgeProvider : BadgeProvider, KoinComponent {

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        return if (searchable is Application && searchable.isSuspended) {
            flowOf(MutableBadge(icon = BadgeIcon(R.drawable.hourglass_bottom_20px)))
        } else {
            flowOf(null)
        }
    }
}