package de.mm20.launcher2.ui.settings.locale

import android.icu.util.Calendar
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.text.Collator

@Serializable
data object CalendarSettingsRoute: NavKey

@Composable
fun CalendarSettingsScreen() {

    val viewModel: CalendarSettingsScreenVM = viewModel()

    val primaryCalendar by viewModel.primaryCalendar.collectAsState(null)
    val secondaryCalendar by viewModel.secondaryCalendar.collectAsState(null)

    val locale = ULocale.getDefault()

    val calendars = remember(locale) {
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        val cals = Calendar.getKeywordValuesForLocale("calendar", locale, false)

        cals.map {
            ULocale.getDefault().setKeywordValue("calendar", it).getDisplayKeywordValue("calendar") to it
        }.sortedWith { el1, el2 ->
            collator.compare(el1.first, el2.first)
        }
    }

    PreferenceScreen(
        title = stringResource(R.string.preference_calendar_system),
    ) {
        item {
            PreferenceCategory {
                ListPreference(
                    title = stringResource(R.string.preference_calendar_system_primary),
                    items = listOf(
                        stringResource(R.string.preference_value_system_default) to null,
                    ) + calendars,
                    value = primaryCalendar,
                    onValueChanged = {
                        viewModel.setPrimaryCalendar(it)
                    }
                )
                ListPreference(
                    title = stringResource(R.string.preference_calendar_system_secondary),
                    items = listOf(
                        stringResource(R.string.preference_value_disabled) to null
                    ) + calendars,
                    value = secondaryCalendar,
                    onValueChanged = {
                        viewModel.setSecondaryCalendar(it)
                    }
                )
            }
        }
    }
}