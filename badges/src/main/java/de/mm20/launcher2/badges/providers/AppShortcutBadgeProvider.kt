package de.mm20.launcher2.badges.providers

import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.graphics.BadgeDrawable
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

class AppShortcutBadgeProvider(
    private val context: Context
) : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> = channelFlow {
        if (searchable is AppShortcut) {
            val componentName = searchable.launcherShortcut.activity
            if (componentName == null) {
                send(null)
                return@channelFlow
            }
            withContext(Dispatchers.IO) {
                val icon = try {
                    context.packageManager.getActivityIcon(
                        componentName
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    return@withContext
                }
                val badge = Badge(icon = BadgeDrawable(context, icon))
                send(badge)
            }
        } else {
            send(null)
        }
    }
}