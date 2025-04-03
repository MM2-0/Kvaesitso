package de.mm20.launcher2.ui.settings.calendarsearch

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.search.CalendarSearchSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CalendarProviderSettingsScreenVM: ViewModel(), KoinComponent {
    private val providerId = MutableStateFlow<String>("")

    fun init(providerId: String) {
        this.providerId.value = providerId
    }

    private val calendarSearchSettings: CalendarSearchSettings by inject()
    private val calendarRepository: CalendarRepository by inject()
    private val pluginService: PluginService by inject()

    val pluginState = providerId.flatMapLatest { pluginService.getPluginWithState(it) }

    val isProviderEnabled = providerId.flatMapLatest { calendarSearchSettings.isProviderEnabled(it) }
    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        calendarSearchSettings.setProviderEnabled(providerId, enabled)
    }

    val calendarLists = providerId
        .flatMapLatest { calendarRepository.getCalendars(it) }
        .map { it.groupBy { it.owner }.toSortedMap(compareBy { it }) }

    val excludedCalendars = calendarSearchSettings.excludedCalendars

    fun setCalendarExcluded(calendarId: String, excluded: Boolean) {
        calendarSearchSettings.setCalendarExcluded(calendarId, excluded)
    }
}