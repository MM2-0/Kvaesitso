package de.mm20.launcher2.ui.settings.calendarwidget

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CalendarWidgetSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()
    private val calendarRepository: CalendarRepository by inject()
    private val permissionsManager: PermissionsManager by inject()

    val hasCalendarPermission = permissionsManager.hasPermission(PermissionGroup.Calendar).asLiveData()

    val excludeAllDayEvents = dataStore.data.map { it.calendarWidget.hideAlldayEvents }.asLiveData()
    fun setExcludeAllDayEvents(excludeAllDayEvents: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCalendarWidget(
                        it.calendarWidget
                            .toBuilder()
                            .setHideAlldayEvents(excludeAllDayEvents)
                    )
                    .build()
            }
        }
    }

    val calendars = liveData {
        emit(calendarRepository.getCalendars())
    }
    val unselectedCalendars =
        dataStore.data.map { it.calendarWidget.excludeCalendarsList }.asLiveData()

    fun setUnselectedCalendars(unselectedCalendars: List<Long>) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCalendarWidget(
                        it.calendarWidget.toBuilder()
                            .clearExcludeCalendars()
                            .addAllExcludeCalendars(unselectedCalendars)
                    )
                    .build()
            }
        }
    }

    fun requestPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Calendar)
    }
}