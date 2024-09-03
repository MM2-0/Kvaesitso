package de.mm20.launcher2.ui.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val navController = LocalNavController.current

    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                // Removed unnecessary preferences for colors, fonts, and cards.
                // The default values should be set directly in your theme or ViewModel.
            }
        }

        if (isAtLeastApiLevel(31)) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
                    val compatModeColors by viewModel.compatModeColors.collectAsState()
                    ListPreference(
                        title = stringResource(R.string.preference_mdy_color_source),
                        items = listOf(
                            stringResource(R.string.preference_mdy_color_source_system) to false,
                            stringResource(R.string.preference_mdy_color_source_wallpaper) to true,
                        ),
                        value = compatModeColors,
                        onValueChanged = {
                            viewModel.setCompatModeColors(it)
                        }
                    )
                }
            }
        }
    }
}