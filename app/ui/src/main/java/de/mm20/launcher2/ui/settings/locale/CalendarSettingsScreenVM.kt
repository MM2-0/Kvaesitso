package de.mm20.launcher2.ui.settings.locale

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.LocaleSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CalendarSettingsScreenVM: ViewModel(), KoinComponent {
    private val localeSettings: LocaleSettings by inject()

    val primaryCalendar = localeSettings.primaryCalendar
    fun setPrimaryCalendar(calendar: String?) {
        localeSettings.setPrimaryCalendar(calendar)
    }

    val secondaryCalendar = localeSettings.secondaryCalendar
    fun setSecondaryCalendar(calendar: String?) {
        localeSettings.setSecondaryCalendar(calendar)
    }
}