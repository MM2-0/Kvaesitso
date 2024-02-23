package de.mm20.launcher2.badges.providers

import android.util.Log
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.MutableBadge
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationBadgeProvider : BadgeProvider, KoinComponent {
    private val notificationRepository: NotificationRepository by inject()

    override fun getBadge(searchable: Searchable): Flow<Badge?> {
        if (searchable !is Application) return flowOf(null)

        val packageName = searchable.componentName.packageName
        return notificationRepository.notifications.map {
            it.filter { it.packageName == packageName && it.canShowBadge }
        }.map {
            if (it.isEmpty()) {
                return@map null
            } else {
                val badge = MutableBadge(
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
                return@map badge
            }
        }
    }
}