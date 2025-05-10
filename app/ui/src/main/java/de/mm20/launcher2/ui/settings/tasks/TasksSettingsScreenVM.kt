package de.mm20.launcher2.ui.settings.tasks

import android.content.Intent
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TasksSettingsScreenVM : ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val iconService: IconService by inject()
    private val calendarSearchSettings: CalendarSearchSettings by inject()

    val tasksApp = appRepository.findOne("org.tasks", Process.myUserHandle())
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isTasksAppInstalled = tasksApp
        .map { it != null }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val hasTasksPermission = permissionsManager.hasPermission(PermissionGroup.Tasks)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun requestTasksPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Tasks)
    }

    fun downloadTasksApp(activity: AppCompatActivity) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = "https://tasks.org/".toUri()
            }
        )
    }

    fun launchTasksApp(activity: AppCompatActivity) {
        viewModelScope.launch {
            tasksApp.first()?.launch(activity, null)
        }
    }

    val isTasksSearchEnabled = calendarSearchSettings.isProviderEnabled("tasks.org")
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun setTasksSearchEnabled(enabled: Boolean) {
        calendarSearchSettings.setProviderEnabled("tasks.org", enabled)
    }
}