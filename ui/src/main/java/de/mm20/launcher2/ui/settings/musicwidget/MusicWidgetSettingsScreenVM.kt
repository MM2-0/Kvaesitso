package de.mm20.launcher2.ui.settings.musicwidget

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.music.MusicRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()
    private val musicRepository: MusicRepository by inject()
    private val dataStore: LauncherDataStore by inject()
    val hasPermission =
        permissionsManager.hasPermission(PermissionGroup.Notifications).asLiveData()


    fun requestNotificationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Notifications)
    }

    val filterSources = dataStore.data.map { it.musicWidget.filterSources }.asLiveData()
    fun setFilterSources(filterSources: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setMusicWidget(
                        it.musicWidget.toBuilder()
                            .setFilterSources(filterSources)
                    )
                    .build()
            }
        }
    }

    fun resetWidget() {
        musicRepository.resetPlayer()
    }

}