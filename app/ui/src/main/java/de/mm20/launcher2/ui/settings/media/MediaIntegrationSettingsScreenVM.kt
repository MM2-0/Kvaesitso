package de.mm20.launcher2.ui.settings.media

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.normalize
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

class MediaIntegrationSettingsScreenVM : ViewModel(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()
    private val musicService: MusicService by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()
    private val dataStore: LauncherDataStore by inject()
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
            val allApps = appRepository.findMany().first().filter { it.profile == AppProfile.Personal }
                .distinctBy { it.componentName.packageName }
            val settings = dataStore.data.map { it.musicWidget }.first()
            val allowList = settings.allowListList
            val denyList = settings.denyListList

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
            }.sortedBy { it.label.normalize() }
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
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setMusicWidget(
                        it.musicWidget.toBuilder()
                            .clearAllowList()
                            .addAllAllowList(allowList)
                            .clearDenyList()
                            .addAllDenyList(denyList)
                    )
                    .build()
            }
        }
    }

}

data class AppListItem(
    val label: String,
    val packageName: String,
    val isMusicApp: Boolean,
    val isChecked: Boolean,
    val icon: Flow<LauncherIcon?>,
)