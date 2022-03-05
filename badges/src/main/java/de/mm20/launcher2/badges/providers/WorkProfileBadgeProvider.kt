package de.mm20.launcher2.badges.providers

import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WorkProfileBadgeProvider : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> = flow {
        if (searchable is LauncherApp && !searchable.isMainProfile || searchable is AppShortcut && !searchable.isMainProfile) {
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