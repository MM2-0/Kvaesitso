package de.mm20.launcher2.ui.settings.main

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.icons.NotificationBadge
import de.mm20.launcher2.ui.locals.LocalNavController

@Composable
fun MainSettingsScreen() {
    val navController = LocalNavController.current
    PreferenceScreen(
        title = stringResource(R.string.settings),
    ) {
        item {
            PreferenceCategory {
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
                        icon = Icons.Rounded.Widgets,
                        title = stringResource(id = R.string.preference_screen_widgets),
                        summary = stringResource(id = R.string.preference_screen_widgets_summary),
                        onClick = {
                            navController?.navigate("settings/widgets")
                        }
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
                        icon = Icons.Rounded.AccountBox,
                        title = stringResource(id = R.string.preference_screen_services),
                        summary = stringResource(id = R.string.preference_screen_services_summary),
                        onClick = {
                            navController?.navigate("settings/accounts")
                        }
                    )
                    Preference(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(id = R.string.preference_screen_debug),
                        summary = stringResource(id = R.string.preference_screen_debug_summary),
                        onClick = {
                            navController?.navigate("settings/debug")
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
}