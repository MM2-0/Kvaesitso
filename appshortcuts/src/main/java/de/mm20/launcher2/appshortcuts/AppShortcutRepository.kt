package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import androidx.core.content.getSystemService
import com.github.promeg.pinyinhelper.Pinyin
import de.mm20.launcher2.search.data.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.FuzzyScore
import java.util.*

interface AppShortcutRepository {
    suspend fun getShortcutsForActivity(
        launcherActivityInfo: LauncherActivityInfo,
        count: Int = 5
    ): List<AppShortcut>

    fun search(query: String): Flow<List<AppShortcut>>
}

internal class AppShortcutRepositoryImpl(
    private val context: Context
) : AppShortcutRepository {

    override suspend fun getShortcutsForActivity(
        launcherActivityInfo: LauncherActivityInfo,
        count: Int,
    ) = withContext(Dispatchers.IO) {
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

    override fun search(query: String) = channelFlow<List<AppShortcut>> {
        val launcherApps = context.getSystemService<LauncherApps>() ?: return@channelFlow send(
            emptyList()
        )

        if (query.length < 3) {
            return@channelFlow send(emptyList())
        }

        withContext(Dispatchers.IO) {
            val shortcutQuery = LauncherApps.ShortcutQuery()
            shortcutQuery.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER)
            val shortcuts = launcherApps.getShortcuts(shortcutQuery, Process.myUserHandle())
                ?.filter {
                    if (it.longLabel != null) {
                        return@filter matches(it.longLabel.toString(), query)
                    }
                    if (it.shortLabel != null) {
                        return@filter matches(it.shortLabel.toString(), query)
                    }
                    return@filter false
                } ?: emptyList()

            val pm = context.packageManager

            send(shortcuts.map {
                val label = try {
                    pm.getApplicationInfo(it.`package`, 0).loadLabel(pm).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    ""
                }
                AppShortcut(
                    context,
                    it,
                    label
                )
            }.sorted())
        }
    }


    private fun matches(label: String, query: String): Boolean {
        val labelLatin = romanize(label)
        val fuzzyScore = FuzzyScore(Locale.getDefault())
        return fuzzyScore.fuzzyScore(label, query) >= query.length * 1.5 ||
                fuzzyScore.fuzzyScore(labelLatin, query) >= query.length * 1.5
    }

    private fun romanize(label: String): String {
        return Pinyin.toPinyin(label, "").lowercase(Locale.getDefault())
    }
}