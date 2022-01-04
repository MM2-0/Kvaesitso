package de.mm20.launcher2.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.icons.NotificationBadge
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun SettingsMainScreen() {
    val navController = LocalNavController.current
    PreferenceScreen(
        title = stringResource(id = R.string.title_activity_settings)
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(id = R.string.preference_screen_appearance),
                    summary = stringResource(id = R.string.preference_screen_appearance_summary),
                    onClick = {
                        navController?.navigate("settings/appearance")
                    }
                )
                Preference(
                    icon = Icons.Rounded.Search,
                    title = stringResource(id = R.string.preference_screen_search),
                    summary = stringResource(id = R.string.preference_screen_search_summary)
                )
                Preference(
                    icon = Icons.Rounded.NotificationBadge,
                    title = stringResource(id = R.string.preference_screen_badges),
                    summary = stringResource(id = R.string.preference_screen_badges_summary),
                    onClick = {
                        navController?.navigate("settings/badges")
                    }
                )
                Preference(
                    icon = Icons.Rounded.LightMode,
                    title = stringResource(id = R.string.preference_screen_weatherwidget),
                    summary = stringResource(id = R.string.preference_screen_weather_summary)
                )
                Preference(
                    icon = Icons.Rounded.Today,
                    title = stringResource(id = R.string.preference_screen_calendarwidget),
                    summary = stringResource(id = R.string.preference_screen_calendar_summary)
                )
                Preference(
                    icon = Icons.Rounded.AccountBox,
                    title = stringResource(id = R.string.preference_screen_services),
                    summary = stringResource(id = R.string.preference_screen_services_summary),
                    onClick = {
                        navController?.navigate("settings/accounts")
                    }
                )
                Preference(
                    icon = Icons.Rounded.Info,
                    title = stringResource(id = R.string.preference_screen_about),
                    summary = stringResource(id = R.string.preference_screen_about_summary),
                    onClick = {
                        navController?.navigate("settings/about")
                    }
                )
            }
        }
    }
}