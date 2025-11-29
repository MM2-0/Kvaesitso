package de.mm20.launcher2.ui.settings.locale

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.LocaleSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocaleSettingsScreenVM: ViewModel(), KoinComponent {
    private val localeSettings: LocaleSettings by inject()

    val timeFormat = localeSettings.timeFormat
    fun setTimeFormat(timeFormat: TimeFormat) {
        localeSettings.setTimeFormat(timeFormat)
    }

    val measurementSystem = localeSettings.measurementSystem
    fun setMeasurementSystem(measurementSystem: MeasurementSystem) {
        localeSettings.setMeasurementSystem(measurementSystem)
    }
}