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
import com.github.promeg.pinyinhelper.Pinyin
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.search.data.AppInstallation
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.FuzzyScore
import java.util.*

interface AppRepository {
    fun search(query: String): Flow<List<Application>>
    fun getAllInstalledApps(includeHidden: Boolean = false): Flow<List<Application>>
    fun getSuspendedPackages(): Flow<List<String>>
}

internal class AppRepositoryImpl(
    private val context: Context,
    hiddenItemsRepository: HiddenItemsRepository,
) : AppRepository {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())
    private val installations = MutableStateFlow<MutableList<AppInstallation>>(mutableListOf())
    private val hiddenItems = hiddenItemsRepository.hiddenItemsKeys
    private val suspendedPackages = MutableStateFlow<List<String>>(emptyList())


    private val profiles: List<UserHandle> =
        launcherApps.profiles.takeIf { it.isNotEmpty() } ?: listOf(Process.myUserHandle())


    private val installingPackages = mutableMapOf<Int, String>()

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

        })
        val apps = profiles.map { p ->
            launcherApps.getActivityList(null, p).mapNotNull { getApplication(it, p) }
        }.flatten()
        installedApps.value = apps

        val packageInstaller = context.packageManager.packageInstaller

        packageInstaller.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onProgressChanged(sessionId: Int, progress: Float) {
                val session = packageInstaller.getSessionInfo(sessionId) ?: return
            }

            override fun onActiveChanged(sessionId: Int, active: Boolean) {
                if (active) onCreated(sessionId)
                else onFinished(sessionId, false)
            }

            override fun onFinished(sessionId: Int, success: Boolean) {
                val pkg = installingPackages[sessionId]
                installingPackages.remove(sessionId)
                val inst = installations.value
                inst.removeAll {
                    it.session.sessionId == sessionId
                }
                installations.value = inst

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
                if (installedApps.value.any { it.`package` == session.appPackageName }) return
                if (session.appLabel.isNullOrBlank() || !session.isActive) return
                val appInstallation = AppInstallation(session)
                val inst = installations.value ?: mutableListOf()
                inst.add(appInstallation)
                installations.value = inst
            }
        })
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

    override fun search(query: String): Flow<List<Application>> = channelFlow {

        merge(installedApps, hiddenItems, installations).collectLatest {
            withContext(Dispatchers.Default) {
                val appResults = mutableListOf<Application>()
                if (query.isEmpty()) {
                    appResults.addAll(installedApps.value)
                    appResults.addAll(installations.value)
                } else {
                    appResults.addAll(installedApps.value.filter {
                        matches(it.label, query)
                    })
                    appResults.addAll(installations.value.filter {
                        matches(it.label, query)
                    })
                }

                val componentName = ComponentName.unflattenFromString(query)
                getActivityByComponentName(componentName)?.let { appResults.add(it) }

                appResults.removeAll { hiddenItems.value.contains(it.key) }

                appResults.sort()

                send(appResults)
            }
        }
    }

    override fun getAllInstalledApps(includeHidden: Boolean): Flow<List<Application>> {
        return if (!includeHidden) {
            channelFlow {
                hiddenItems.collectLatest { hidden ->
                    installedApps.collectLatest { apps ->
                        send(apps.filter { !hidden.contains(it.key) })
                    }
                }
            }
        } else {
            installedApps
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

    private fun getActivityByComponentName(componentName: ComponentName?): Application? {
        componentName ?: return null
        val intent = Intent().setComponent(componentName)
        val lai = launcherApps.resolveActivity(intent, Process.myUserHandle())
        return lai?.let {
            LauncherApp(context, lai)
        }
    }
}