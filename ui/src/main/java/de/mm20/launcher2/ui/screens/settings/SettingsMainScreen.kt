package de.mm20.launcher2.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun SettingsMainScreen() {
    PreferenceScreen(
        title = stringResource(id = R.string.title_activity_settings)
    ) {
        item {
            Preference(
                icon = Icons.Rounded.Palette,
                title = stringResource(id = R.string.preference_screen_appearance),
                summary = stringResource(id = R.string.preference_screen_appearance_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.Search,
                title = stringResource(id = R.string.preference_screen_search),
                summary = stringResource(id = R.string.preference_screen_search_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.Palette,
                title = stringResource(id = R.string.preference_screen_badges),
                summary = stringResource(id = R.string.preference_screen_badges_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.LightMode,
                title = stringResource(id = R.string.preference_screen_weather),
                summary = stringResource(id = R.string.preference_screen_weather_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.Today,
                title = stringResource(id = R.string.preference_screen_calendar),
                summary = stringResource(id = R.string.preference_screen_calendar_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.AccountBox,
                title = stringResource(id = R.string.preference_screen_services),
                summary = stringResource(id = R.string.preference_screen_services_summary)
            )
        }
        item {
            Preference(
                icon = Icons.Rounded.Info,
                title = stringResource(id = R.string.preference_screen_about),
                summary = stringResource(id = R.string.preference_screen_about_summary)
            )
        }
    }
}