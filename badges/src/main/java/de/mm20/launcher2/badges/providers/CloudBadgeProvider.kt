package de.mm20.launcher2.badges.providers

import android.util.Log
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CloudBadgeProvider: BadgeProvider {
    override fun getBadge(badgeKey: String): Flow<Badge?> = flow {
        when(badgeKey) {
            "gdrive://" -> emit(Badge(iconRes = R.drawable.ic_badge_gdrive))
            "onedrive://" -> emit(Badge(iconRes = R.drawable.ic_badge_onedrive))
            "owncloud://" -> emit(Badge(iconRes = R.drawable.ic_badge_owncloud))
            "nextcloud://" -> emit(Badge(iconRes = R.drawable.ic_badge_nextcloud))
            else -> emit(null)
        }
    }
}