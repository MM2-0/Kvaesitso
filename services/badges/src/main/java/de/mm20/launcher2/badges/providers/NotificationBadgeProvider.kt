package de.mm20.launcher2.badges.providers

import android.app.Notification
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationBadgeProvider : BadgeProvider, KoinComponent {
    private val notificationRepository: NotificationRepository by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> = channelFlow {
        if (searchable is LauncherApp) {
            val packageName = searchable.`package`
            notificationRepository.notifications.map {
                it.filter { it.packageName == packageName }
            }.collectLatest {
                if (it.isEmpty() || it.none { it.canShowBadge }) {
                    send(null)
                } else {
                    val badge = Badge(
                        number = it.sumOf {
                            if (it.canShowBadge && !it.isGroupSummary) it.number
                            else 0
                        },
                        progress = it.mapNotNull {
                            val progress = it.progress ?: return@mapNotNull null
                            val progressMax = it.progressMax ?: return@mapNotNull null
                            return@mapNotNull progress.toFloat() / progressMax.toFloat()
                        }
                            .takeIf { it.isNotEmpty() }
                            ?.let {
                                it.sumOf { it.toDouble() }.toFloat() / it.size
                            }
                    )
                    send(badge)
                }
            }
        } else {
            send(null)
        }
    }
}