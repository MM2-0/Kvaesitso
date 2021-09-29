package de.mm20.launcher2.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun SettingsAppearanceScreen() {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                val theme by remember {
                    dataStore.data.map { it.appearance.theme }
                }.collectAsState(initial = Settings.AppearanceSettings.Theme.System)
                ListPreference(
                    title = stringResource(id = R.string.preference_theme),
                    items = listOf(
                        stringResource(id = R.string.preference_theme_system) to Settings.AppearanceSettings.Theme.System,
                        stringResource(id = R.string.preference_theme_light) to Settings.AppearanceSettings.Theme.Light,
                        stringResource(id = R.string.preference_theme_dark) to Settings.AppearanceSettings.Theme.Dark,
                    ),
                    value = theme,
                    onValueChanged = { newValue ->
                        scope.launch {
                            dataStore.updateData {
                                it.toBuilder()
                                    .setAppearance(it.appearance.toBuilder().setTheme(newValue))
                                    .build()
                            }
                        }
                    }
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    onClick = {
                        navController?.navigate("settings/appearance/colors")
                    })
            }
        }
        item {
            PreferenceCategory(title = stringResource(id = R.string.preference_category_clock_widget)) {
                Preference(
                    title = stringResource(id = R.string.preference_clock_widget_style),
                    onClick = {
                        navController?.navigate("settings/appearance/clock")
                    }
                )
            }
        }
    }
}