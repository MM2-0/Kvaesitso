package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import de.mm20.launcher2.ktx.normalize
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.FuzzyScore
import java.util.Locale

interface AppRepository : SearchableRepository<Application> {
    fun findMany(): Flow<ImmutableList<Application>>
}

internal class AppRepositoryImpl(
    private val context: Context,
) : AppRepository {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val suspendedPackages = MutableStateFlow<List<String>>(emptyList())


    private val profiles: List<UserHandle>
        get() = launcherApps.profiles.takeIf { it.isNotEmpty() } ?: listOf(Process.myUserHandle())

    private val mutex = Mutex()

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageNames.contains(it.componentName.packageName) && it.user == user }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        apps.addAll(getApplications(packageName, user))
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        for (packageName in packageNames) {
                            apps.addAll(getApplications(packageName, user))
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.addAll(getApplications(packageName, user))
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        installedApps.value = apps

                    }
                }
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle
            ) {
                onPackageChanged(packageName, user)
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = true)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>?,
                user: UserHandle?
            ) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = false)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

        }, Handler(Looper.getMainLooper()))
        scope.launch {
            mutex.withLock {
                val apps = profiles.map { p ->
                    try {
                        launcherApps.getActivityList(null, p).mapNotNull { getApplication(it) }
                    } catch (e: SecurityException) {
                        emptyList()
                    }
                }.flatten()
                installedApps.value = apps
            }
        }
    }

    private fun getApplications(packageName: String, userHandle: UserHandle): List<LauncherApp> {
        if (packageName == context.packageName) return emptyList()

        return try {
            launcherApps.getActivityList(packageName, userHandle)
                .mapNotNull { getApplication(it) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }


    private fun getApplication(
        launcherActivityInfo: LauncherActivityInfo
    ): LauncherApp? {
        if (launcherActivityInfo.applicationInfo.packageName == context.packageName && !context.packageName.endsWith(
                ".debug"
            )
        ) return null
        return LauncherApp(context, launcherActivityInfo)
    }

    override fun findMany(): Flow<ImmutableList<Application>> {
        return installedApps.map { it.toImmutableList() }
    }

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<LauncherApp>> {
        return installedApps.map { apps ->
            withContext(Dispatchers.Default) {
                val appResults = mutableListOf<LauncherApp>()
                if (query.isEmpty()) {
                    appResults.addAll(apps)
                } else {
                    appResults.addAll(apps.filter {
                        matches(it.label, query)
                    })

                    val componentName = ComponentName.unflattenFromString(query)
                    getActivityByComponentName(componentName)?.let { appResults.add(it) }
                }
                appResults.sort()
                appResults.toImmutableList()
            }
        }
    }

    private fun matches(label: String, query: String): Boolean {
        val normalizedLabel = label.normalize()
        val normalizedQuery = query.normalize()
        if (normalizedLabel.contains(normalizedQuery)) return true
        val fuzzyScore = FuzzyScore(Locale.getDefault())
        return fuzzyScore.fuzzyScore(normalizedLabel, normalizedQuery) >= query.length * 1.5
    }

    private fun getActivityByComponentName(componentName: ComponentName?): LauncherApp? {
        componentName ?: return null
        val intent = Intent().setComponent(componentName)
        val lai = launcherApps.resolveActivity(intent, Process.myUserHandle())
        return lai?.let {
            LauncherApp(context, lai)
        }
    }
}