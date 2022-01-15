package de.mm20.launcher2.badges.providers

import android.content.Context
import android.os.Process
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.R
import de.mm20.launcher2.ktx.getSerialNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WorkProfileBadgeProvider(private val context: Context) : BadgeProvider {
    override fun getBadge(badgeKey: String): Flow<Badge?> = flow {
        if (badgeKey.startsWith("profile://")) {
            val serialNumber = badgeKey.substring(10).toLong()
            if (serialNumber != Process.myUserHandle().getSerialNumber(context)) {
                emit(
                    Badge(
                        iconRes = R.drawable.ic_badge_workprofile
                    )
                )
            } else {
                emit(null)
            }
        } else {
            emit(null)
        }
    }
}