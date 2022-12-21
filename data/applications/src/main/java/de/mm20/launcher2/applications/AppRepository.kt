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
import android.util.Log
import de.mm20.launcher2.ktx.normalize
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.FuzzyScore
import java.util.*

interface AppRepository {
    fun getAllInstalledApps(): Flow<List<LauncherApp>>
    fun getSuspendedPackages(): Flow<List<String>>
    fun search(query: String): Flow<ImmutableList<LauncherApp>>
}

internal class AppRepositoryImpl(
    private val context: Context,
) : AppRepository {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val suspendedPackages = MutableStateFlow<List<String>>(emptyList())


    private val profiles: List<UserHandle> =
        launcherApps.profiles.takeIf { it.isNotEmpty() } ?: listOf(Process.myUserHandle())


    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                installedApps.value =
                    installedApps.value.filter { !packageNames.contains(it.`package`) }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                val apps = installedApps.value.toMutableList()
                apps.removeAll { packageName == it.`package` }
                apps.addAll(getApplications(packageName))
                installedApps.value = apps
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                val apps = installedApps.value.toMutableList()
                for (packageName in packageNames) {
                    apps.addAll(getApplications(packageName))
                }
                installedApps.value = apps
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                Log.d("MM20", "App installed: $packageName")
                val apps = installedApps.value.toMutableList()
                apps.addAll(getApplications(packageName))
                installedApps.value = apps
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                installedApps.value =
                    installedApps.value.filter { packageName != (it.`package`) || it.getUser() != user }
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle
            ) {
                super.onShortcutsChanged(packageName, shortcuts, user)
                onPackageChanged(packageName, user)
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                super.onPackagesSuspended(packageNames, user)
                packageNames ?: return
                suspendedPackages.value = suspendedPackages.value + packageNames
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>?,
                user: UserHandle?
            ) {
                super.onPackagesUnsuspended(packageNames, user)
                packageNames ?: return
                suspendedPackages.value =
                    suspendedPackages.value.filter { packageNames.contains(it) }
            }

        }, Handler(Looper.getMainLooper()))
        val apps = profiles.map { p ->
            launcherApps.getActivityList(null, p).mapNotNull { getApplication(it, p) }
        }.flatten()
        installedApps.value = apps
    }


    override fun getSuspendedPackages(): Flow<List<String>> {
        return suspendedPackages
    }

    private fun getApplications(packageName: String): List<LauncherApp> {
        if (packageName == context.packageName) return emptyList()

        return profiles.map { p ->
            launcherApps.getActivityList(packageName, p).mapNotNull { getApplication(it, p) }
        }.flatten()
    }


    private fun getApplication(
        launcherActivityInfo: LauncherActivityInfo,
        profile: UserHandle
    ): LauncherApp? {
        if (launcherActivityInfo.applicationInfo.packageName == context.packageName && !context.packageName.endsWith(
                ".debug"
            )
        ) return null
        return LauncherApp(context, launcherActivityInfo)
    }

    override fun search(query: String): Flow<ImmutableList<LauncherApp>> = channelFlow {

        installedApps.collectLatest { apps ->
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

                send(appResults.toImmutableList())
            }
        }
    }

    override fun getAllInstalledApps(): Flow<List<LauncherApp>> {
        return installedApps
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