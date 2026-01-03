package de.mm20.launcher2.ui.settings.media

import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.media.MediaSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator
import kotlin.math.roundToInt

class MediaIntegrationSettingsScreenVM : ViewModel(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()
    private val musicService: MusicService by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    private val mediaSettings: MediaSettings by inject()

    val hasPermission =
        permissionsManager.hasPermission(PermissionGroup.Notifications)

    val loading = mutableStateOf(false)


    fun requestNotificationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Notifications)
    }

    val appList = mutableStateOf(emptyList<AppListItem>())

    fun resetWidget() {
        musicService.resetPlayer()
    }

    fun onResume(density: Float) {
        loading.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val musicApps = musicService.getInstalledPlayerPackages()
            val allApps = appRepository.findMany().first { it.isNotEmpty() }.filter { it.user == Process.myUserHandle() }
                .distinctBy { it.componentName.packageName }
            val settings = mediaSettings.first()
            val allowList = settings.allowList
            val denyList = settings.denyList

            appList.value = allApps.map {
                AppListItem(
                    label = it.label,
                    packageName = it.componentName.packageName,
                    isMusicApp = musicApps.contains(it.componentName.packageName),
                    isChecked = allowList.contains(it.componentName.packageName) || (!denyList.contains(it.componentName.packageName) && musicApps.contains(
                        it.componentName.packageName
                    )),
                    icon = iconService.getIcon(it, (32 * density).roundToInt())
                        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(10000))
                )
            }.sorted()
            loading.value = false
        }
    }

    fun onAppChecked(app: AppListItem, checked: Boolean) {
        val list = appList.value.toMutableList()
        val index = list.indexOf(app)
        list[index] = app.copy(isChecked = checked)
        appList.value = list
        saveState()
    }

    private fun saveState() {
        val allowList = mutableListOf<String>()
        val denyList = mutableListOf<String>()
        val appList = appList.value

        for (app in appList) {
            if (app.isChecked && !app.isMusicApp) {
                allowList.add(app.packageName)
            } else if (!app.isChecked && app.isMusicApp) {
                denyList.add(app.packageName)
            }
        }
        mediaSettings.setLists(allowList.toSet(), denyList.toSet())
    }

}

data class AppListItem(
    val label: String,
    val packageName: String,
    val isMusicApp: Boolean,
    val isChecked: Boolean,
    val icon: Flow<LauncherIcon?>,
): Comparable<AppListItem> {
    override fun compareTo(other: AppListItem): Int {
        val label1 = label
        val label2 = other.label
        return Collator.getInstance().apply { strength = Collator.SECONDARY }
            .compare(label1, label2)
    }

}