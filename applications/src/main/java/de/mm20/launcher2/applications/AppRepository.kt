package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.AppInstallation
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(
    val context: Context,
    hiddenItemsRepository: HiddenItemsRepository,
    badgeProvider: BadgeProvider
    ) : BaseSearchableRepository() {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    val applications = MediatorLiveData<List<Application>>()


    private val installedApps = MutableLiveData<List<Application>>(emptyList())
    private val installations = MutableLiveData<MutableList<AppInstallation>>(mutableListOf())
    private val hiddenItemKeys = hiddenItemsRepository.hiddenItemsKeys

    private val installingPackages = mutableMapOf<Int, String>()

    private val profiles: List<UserHandle> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        launcherApps.profiles.takeIf { it.isNotEmpty() } ?: listOf(Process.myUserHandle())
    } else {
        listOf(Process.myUserHandle())
    }


    init {

        applications.addSource(installedApps) {
            launch { updateAppsForDisplay() }
        }
        applications.addSource(installations) {
            launch { updateAppsForDisplay() }
        }

        applications.addSource(hiddenItemKeys) {
            launch { updateAppsForDisplay() }
        }

        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                installedApps.value = installedApps.value?.filter { !packageNames.contains(it.`package`) }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                val apps = installedApps.value?.toMutableList() ?: return
                apps.removeAll { packageName == it.`package` }
                apps.addAll(getApplications(packageName))
                installedApps.value = apps
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                val apps = installedApps.value?.toMutableList() ?: return
                for (packageName in packageNames) {
                    apps.addAll(getApplications(packageName))
                }
                installedApps.value = apps
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                Log.d("MM20", "App installed: $packageName")
                val apps = installedApps.value?.toMutableList() ?: return
                apps.addAll(getApplications(packageName))
                installedApps.value = apps
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                installedApps.value = installedApps.value?.filter { packageName != (it.`package`) }
            }

            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
                super.onShortcutsChanged(packageName, shortcuts, user)
                onPackageChanged(packageName, user)
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                super.onPackagesSuspended(packageNames, user)
                packageNames?.forEach {
                    badgeProvider.setBadge("app://$it", Badge(iconRes = R.drawable.ic_badge_suspended))
                }
            }

            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) {
                super.onPackagesUnsuspended(packageNames, user)
                packageNames?.forEach {
                    badgeProvider.removeBadge("app://$it")
                }
            }

        })


        val packageInstaller = context.packageManager.packageInstaller

        packageInstaller.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onProgressChanged(sessionId: Int, progress: Float) {
                val session = packageInstaller.getSessionInfo(sessionId) ?: return
                val pkg = session.appPackageName ?: return
                badgeProvider.updateBadge("app://$pkg", Badge(progress = progress))
            }

            override fun onActiveChanged(sessionId: Int, active: Boolean) {
                if (active) onCreated(sessionId)
                else onFinished(sessionId, false)
            }

            override fun onFinished(sessionId: Int, success: Boolean) {
                val pkg = installingPackages[sessionId]
                installingPackages.remove(sessionId)
                val key = "app://$pkg"
                val badge = badgeProvider.getBadge(key)?.apply { progress = null }
                        ?: Badge()
                badgeProvider.setBadge(key, badge)
                val inst = installations.value ?: return
                inst.removeAll {
                    it.session.sessionId == sessionId
                }
                installations.postValue(inst)

            }

            override fun onBadgingChanged(sessionId: Int) {
                val inst = installations.value ?: mutableListOf()
                inst.removeAll {
                    it.session.sessionId == sessionId
                }
                onCreated(sessionId)
            }

            override fun onCreated(sessionId: Int) {
                val session = packageInstaller.getSessionInfo(sessionId) ?: return
                installingPackages[sessionId] = session.appPackageName ?: return
                if (installedApps.value?.any { it.`package` == session.appPackageName } == true) return
                if (session.appLabel.isNullOrBlank() || !session.isActive) return
                val appInstallation = AppInstallation(session)
                val inst = installations.value ?: mutableListOf()
                inst.add(appInstallation)
                installations.postValue(inst)
            }
        })


        val apps = profiles.map { p -> launcherApps.getActivityList(null, p).mapNotNull { getApplication(it, p) } }.flatten()
        installedApps.value = apps
    }

    override suspend fun search(query: String) {
        updateAppsForDisplay()
    }

    private suspend fun updateAppsForDisplay() {
        val query = searchRepository.currentQuery.value ?: ""

        val componentName = ComponentName.unflattenFromString(query)

        val apps = withContext(Dispatchers.Default) {
            val hiddenItems = hiddenItemKeys.value ?: emptyList()
            val installed = installedApps.value ?: emptyList()
            val installing = installations.value ?: emptyList<AppInstallation>()
            val results = mutableListOf<Application>()
            results.addAll(installed)
            results.addAll(installing)
            if (query.isNotEmpty()) {
                results.removeAll { !it.label.contains(query, ignoreCase = true) }
                getActivityByComponentName(componentName)?.let { results.add(it) }
            }
            results.removeAll { hiddenItems.contains(it.key) }
            results.sort()
            results
        }

        applications.value = apps
    }

    private fun getActivityByComponentName(componentName: ComponentName?): Application? {
        if (!LauncherPreferences.instance.searchActivities) return null
        componentName ?: return null
        val intent = Intent().setComponent(componentName)
        val lai = launcherApps.resolveActivity(intent, Process.myUserHandle())
        return lai?.let {
            LauncherApp(context, lai)
        }
    }

    private fun getApplication(launcherActivityInfo: LauncherActivityInfo, profile: UserHandle): Application? {
        if (launcherActivityInfo.applicationInfo.packageName == context.packageName && !context.packageName.endsWith(".debug")) return null
        return LauncherApp(context, launcherActivityInfo)
    }

    private fun getApplications(packageName: String): List<Application> {
        if (packageName == context.packageName) return emptyList()

        return profiles.map { p -> launcherApps.getActivityList(packageName, p).mapNotNull { getApplication(it, p) } }.flatten()
    }
}