package de.mm20.launcher2.ui.settings.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.about.AboutSettingsRoute
import de.mm20.launcher2.ui.settings.appearance.AppearanceSettingsRoute
import de.mm20.launcher2.ui.settings.backup.BackupSettingsRoute
import de.mm20.launcher2.ui.settings.debug.DebugSettingsRoute
import de.mm20.launcher2.ui.settings.gestures.GesturesSettingsRoute
import de.mm20.launcher2.ui.settings.homescreen.HomescreenSettingsRoute
import de.mm20.launcher2.ui.settings.icons.IconsSettingsRoute
import de.mm20.launcher2.ui.settings.integrations.IntegrationsSettingsRoute
import de.mm20.launcher2.ui.settings.locale.LocaleSettingsRoute
import de.mm20.launcher2.ui.settings.plugins.PluginsSettingsRoute
import de.mm20.launcher2.ui.settings.search.SearchSettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object MainRoute: NavKey

@Composable
fun MainSettingsScreen() {
    val backStack = LocalBackStack.current
    PreferenceScreen(
        title = stringResource(R.string.settings),
    ) {
        item {
            PreferenceCategory {
                Preference(
                    icon = R.drawable.palette_24px,
                    title = stringResource(id = R.string.preference_screen_appearance),
                    summary = stringResource(id = R.string.preference_screen_appearance_summary),
                    onClick = {
                        backStack.add(AppearanceSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.home_24px,
                    title = stringResource(id = R.string.preference_screen_homescreen),
                    summary = stringResource(id = R.string.preference_screen_homescreen_summary),
                    onClick = {
                        backStack.add(HomescreenSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.apps_24px,
                    title = stringResource(id = R.string.preference_screen_icons),
                    summary = stringResource(id = R.string.preference_screen_icons_summary),
                    onClick = {
                        backStack.add(IconsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.search_24px,
                    title = stringResource(id = R.string.preference_screen_search),
                    summary = stringResource(id = R.string.preference_screen_search_summary),
                    onClick = {
                        backStack.add(SearchSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.gesture_24px,
                    title = stringResource(id = R.string.preference_screen_gestures),
                    summary = stringResource(id = R.string.preference_screen_gestures_summary),
                    onClick = {
                        backStack.add(GesturesSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.power_24px,
                    title = stringResource(id = R.string.preference_screen_integrations),
                    summary = stringResource(id = R.string.preference_screen_integrations_summary),
                    onClick = {
                        backStack.add(IntegrationsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.extension_24px,
                    title = stringResource(id = R.string.preference_screen_plugins),
                    summary = stringResource(id = R.string.preference_screen_plugins_summary),
                    onClick = {
                        backStack.add(PluginsSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.translate_24px,
                    title = stringResource(id = R.string.preference_screen_locale),
                    summary = stringResource(id = R.string.preference_screen_locale_summary),
                    onClick = {
                        backStack.add(LocaleSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.settings_backup_restore_24px,
                    title = stringResource(id = R.string.preference_screen_backup),
                    summary = stringResource(id = R.string.preference_screen_backup_summary),
                    onClick = {
                        backStack.add(BackupSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.bug_report_24px,
                    title = stringResource(id = R.string.preference_screen_debug),
                    summary = stringResource(id = R.string.preference_screen_debug_summary),
                    onClick = {
                        backStack.add(DebugSettingsRoute)
                    }
                )
                Preference(
                    icon = R.drawable.info_24px,
                    title = stringResource(id = R.string.preference_screen_about),
                    summary = stringResource(id = R.string.preference_screen_about_summary),
                    onClick = {
                        backStack.add(AboutSettingsRoute)
                    }
                )
            }
        }
    }
}