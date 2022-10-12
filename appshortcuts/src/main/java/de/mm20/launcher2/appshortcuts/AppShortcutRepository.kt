package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.normalize
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.LauncherShortcut
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.FuzzyScore
import java.util.*

interface AppShortcutRepository: SearchableRepository<AppShortcut> {
    suspend fun getShortcutsForActivity(
        launcherActivityInfo: LauncherActivityInfo,
        count: Int = 5
    ): List<LauncherShortcut>

    suspend fun getShortcutsConfigActivities(): List<LauncherApp>

    fun removePinnedShortcut(shortcut: LauncherShortcut)
}

internal class AppShortcutRepositoryImpl(
    private val context: Context,
    private val permissionsManager: PermissionsManager,
    private val dataStore: LauncherDataStore,
) : AppShortcutRepository {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

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
        val appShortcuts = mutableListOf<LauncherShortcut>()
        appShortcuts.addAll(shortcuts
            ?.let {
                if (it.size > count) it.subList(0, count)
                else it
            }
            ?.map {
                LauncherShortcut(
                    context,
                    it,
                )
            } ?: emptyList())
        appShortcuts
    }

    override fun search(query: String) = channelFlow<ImmutableList<AppShortcut>> {
        if (query.length < 3) {
            send(persistentListOf())
            return@channelFlow
        }
        withContext(Dispatchers.IO) {
            if (!permissionsManager.checkPermissionOnce(PermissionGroup.AppShortcuts)) {
                send(persistentListOf())
                return@withContext
            }
            dataStore.data.map { it.appShortcutSearch.enabled }.collectLatest { enabled ->
                if (!enabled) {
                    send(persistentListOf())
                    return@collectLatest
                }

                shortcutChangeEmitter.collectLatest {
                    val launcherApps =
                        context.getSystemService<LauncherApps>() ?: return@collectLatest send(
                            persistentListOf()
                        )

                    val shortcutQuery = LauncherApps.ShortcutQuery()
                    shortcutQuery.setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
                    )
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


                    send(
                        shortcuts.mapNotNull {
                            LauncherShortcut(
                                context,
                                it
                            )
                        }.toImmutableList()
                    )
                }
            }
        }
    }

    private val shortcutChangeEmitter = callbackFlow {
        send(Unit)
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            }

            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) {
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) {
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle
            ) {
                super.onShortcutsChanged(packageName, shortcuts, user)
                trySend(Unit)
            }

        }

        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(500), 1)

    override fun removePinnedShortcut(shortcut: LauncherShortcut) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!launcherApps.hasShortcutHostPermission()) return
        val pinnedShortcutsQuery = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        val userHandle = shortcut.launcherShortcut.userHandle
        val allPinned = launcherApps.getShortcuts(pinnedShortcutsQuery, userHandle)

        if (allPinned == null) {
            Log.e("MM20", "Could not remove shortcut ${shortcut.key}: shortcut query returned null")
            return
        }

        launcherApps.pinShortcuts(
            shortcut.launcherShortcut.`package`,
            allPinned.filter { it.id != shortcut.launcherShortcut.id }.map { it.id },
            userHandle
        )
    }

    override suspend fun getShortcutsConfigActivities(): List<LauncherApp> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val results = mutableListOf<LauncherApp>()
        val profiles = launcherApps.profiles
        for (profile in profiles) {
            val activities = launcherApps.getShortcutConfigActivityList(null, profile)
            results.addAll(
                activities.map {
                    LauncherApp(
                        context, it
                    )
                }
            )
        }
        return results.sorted()
    }


    private fun matches(label: String, query: String): Boolean {
        val labelLatin = label.normalize()
        val fuzzyScore = FuzzyScore(Locale.getDefault())
        return fuzzyScore.fuzzyScore(label, query) >= query.length * 1.5 ||
                fuzzyScore.fuzzyScore(labelLatin, query.normalize()) >= query.length * 1.5
    }
}