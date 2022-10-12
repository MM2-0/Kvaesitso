package de.mm20.launcher2.badges.providers

import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.graphics.BadgeDrawable
import de.mm20.launcher2.search.data.LauncherShortcut
import de.mm20.launcher2.search.data.LegacyShortcut
import de.mm20.launcher2.search.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

class AppShortcutBadgeProvider(
    private val context: Context
) : BadgeProvider {
    override fun getBadge(searchable: Searchable): Flow<Badge?> = channelFlow {
        if (searchable is LauncherShortcut) {
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
        } else if (searchable is LegacyShortcut) {
            val packageName = searchable.packageName
            if (packageName == null) {
                send(null)
                return@channelFlow
            }
            withContext(Dispatchers.IO) {
                val icon = try {
                    context.packageManager.getApplicationIcon(
                        packageName
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