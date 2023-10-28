package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.search.AppProfile
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WorkProfileBadgeProvider : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> = flow {
        if (searchable is Application && searchable.profile == AppProfile.Work || searchable is AppShortcut && searchable.profile == AppProfile.Work) {
            emit(
                Badge(
                    iconRes = R.drawable.ic_badge_workprofile
                )
            )
        } else {
            emit(null)
        }
    }
}