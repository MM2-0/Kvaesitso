package de.mm20.launcher2.ui.settings.appearance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.Font
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.value
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.theme.getTypography

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val themeName by viewModel.themeName.collectAsStateWithLifecycle(null)
    val compatModeColors by viewModel.compatModeColors.collectAsState()
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                val theme by viewModel.colorScheme.collectAsState()
                ListPreference(
                    title = stringResource(id = R.string.preference_theme),
                    items = listOf(
                        stringResource(id = R.string.preference_theme_system) to ColorScheme.System,
                        stringResource(id = R.string.preference_theme_light) to ColorScheme.Light,
                        stringResource(id = R.string.preference_theme_dark) to ColorScheme.Dark,
                    ),
                    value = theme,
                    onValueChanged = { newValue ->
                        if (newValue == null) return@ListPreference
                        viewModel.setColorScheme(newValue)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    summary = themeName,
                    onClick = {
                        navController?.navigate("settings/appearance/colors")
                    },
                    icon = Icons.Rounded.Palette,
                )
                val font by viewModel.font.collectAsState()
                ListPreference(
                    title = stringResource(R.string.preference_font),
                    items = listOf(
                        "Outfit" to Font.Outfit,
                        stringResource(R.string.preference_font_system) to Font.System,
                    ),
                    value = font,
                    onValueChanged = {
                        if (it != null) viewModel.setFont(it)
                    },
                    itemLabel = {
                        val typography = remember(it.value) {
                            getTypography(context, it.value)
                        }
                        Text(it.first, style = typography.titleMedium)
                    },
                    icon = Icons.Rounded.TextFields,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_shapes),
                    summary = themeName,
                    onClick = {
                        navController?.navigate("settings/appearance/shapes")
                    },
                    icon = Icons.Rounded.CropSquare,
                )

                Preference(
                    title = stringResource(R.string.preference_cards),
                    summary = stringResource(R.string.preference_cards_summary),
                    onClick = {
                        navController?.navigate("settings/appearance/cards")
                    }
                )
            }
        }

        if (isAtLeastApiLevel(31)) {
            item {
                PreferenceCategory(stringResource(R.string.preference_category_advanced)) {
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