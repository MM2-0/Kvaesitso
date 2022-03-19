package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import androidx.core.content.getSystemService
import de.mm20.launcher2.search.data.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppShortcutRepository {
    suspend fun getShortcutsForActivity(launcherActivityInfo: LauncherActivityInfo, count: Int = 5): List<AppShortcut>


}

internal class AppShortcutRepositoryImpl(
    private val context: Context
): AppShortcutRepository {
    override suspend fun getShortcutsForActivity(
        launcherActivityInfo: LauncherActivityInfo,
        count: Int,
    )  = withContext(Dispatchers.IO){
        val launcherApps = context.getSystemService<LauncherApps>()!!
        if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(launcherActivityInfo.applicationInfo.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val shortcuts = try {
            launcherApps.getShortcuts(query, launcherActivityInfo.user)
        } catch (e: IllegalStateException) {
            emptyList()
        }
        val appShortcuts = mutableListOf<AppShortcut>()
        appShortcuts.addAll(shortcuts
            ?.let {
                if (it.size > count) it.subList(0, count)
                else it
            }
            ?.map {
            AppShortcut(
                context,
                it,
                launcherActivityInfo.label.toString()
            )
        } ?: emptyList())
        appShortcuts
    }
}