package de.mm20.launcher2.badges

import android.content.Context
import android.util.Log
import de.mm20.launcher2.badges.providers.*
import de.mm20.launcher2.badges.providers.BadgeProvider
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface BadgeRepository {
    fun getBadge(badgeKey: String): Flow<Badge?>
}

internal class BadgeRepositoryImpl(private val context: Context) : BadgeRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val badgeProviders = MutableStateFlow<List<BadgeProvider>>(emptyList())

    init {
        scope.launch {
            dataStore.data.map { it.badges }.distinctUntilChanged().collectLatest {
                val providers = mutableListOf<BadgeProvider>()
                if (it.notifications) {
                    providers += NotificationBadgeProvider()
                }
                if (it.cloudFiles) {
                    providers += CloudBadgeProvider()
                }
                if (it.shortcuts) {
                    providers += AppShortcutBadgeProvider(context)
                }
                if (it.suspendedApps) {
                    providers += SuspendedAppsBadgeProvider()
                }
                providers += WorkProfileBadgeProvider(context)
                badgeProviders.value = providers
            }
        }
    }

    override fun getBadge(badgeKey: String): Flow<Badge?> = channelFlow {
        badgeProviders.collectLatest { providers ->
            if (providers.isEmpty()) {
                send(null)
                return@collectLatest
            }
            combine(providers.map { it.getBadge(badgeKey) }) { badges ->
                if (badges.all { it == null }) {
                    return@combine null
                }
                val badge = Badge()
                var progresses = 0
                badges.filterNotNull().forEach {
                    if (it.icon != null && badge.icon == null) badge.icon = it.icon
                    if (it.iconRes != null && badge.iconRes == null) badge.iconRes = it.iconRes
                    it.number?.let { a ->
                        badge.number?.let { b -> badge.number = a + b } ?: run {
                            badge.number = a
                        }
                    }
                    it.progress?.let { a ->
                        badge.progress?.let {
                                b -> badge.progress = a + b
                        } ?: run {
                            badge.progress = a
                        }
                        progresses++
                    }
                }
                if (progresses > 0) {
                    badge.progress?.let { badge.progress = it / progresses }
                }
                return@combine badge
            }.collectLatest {
                send(it)
            }
        }
    }

}