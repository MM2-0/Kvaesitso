package de.mm20.launcher2.ui.settings.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
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
import de.mm20.launcher2.ui.settings.transparencies.TransparencySchemesSettingsRoute
import de.mm20.launcher2.ui.settings.typography.TypographiesSettingsRoute
import de.mm20.launcher2.ui.theme.getTypography

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val navController = LocalNavController.current
    val colorThemeName by viewModel.colorThemeName.collectAsStateWithLifecycle(null)
    val typographyThemeName by viewModel.typographyThemeName.collectAsStateWithLifecycle(null)
    val shapeThemeName by viewModel.shapeThemeName.collectAsStateWithLifecycle(null)
    val transparencyThemeName by viewModel.transparencyThemeName.collectAsStateWithLifecycle(null)
    val compatModeColors by viewModel.compatModeColors.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null) {
            return@rememberLauncherForActivityResult
        }
        navController?.navigate(ImportThemeSettingsRoute(it.toString()))
    }

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
                    summary = colorThemeName,
                    onClick = {
                        navController?.navigate("settings/appearance/colors")
                    },
                    icon = Icons.Rounded.Palette,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_typography),
                    summary = typographyThemeName,
                    onClick = {
                        navController?.navigate(TypographiesSettingsRoute)
                    },
                    icon = Icons.Rounded.TextFields,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_shapes),
                    summary = shapeThemeName,
                    onClick = {
                        navController?.navigate("settings/appearance/shapes")
                    },
                    icon = Icons.Rounded.CropSquare,
                )
                Preference(
                    title = stringResource(id = R.string.preference_screen_transparencies),
                    summary = transparencyThemeName,
                    onClick = {
                        navController?.navigate(TransparencySchemesSettingsRoute)
                    },
                    icon = Icons.Rounded.Opacity,
                )
            }
        }

        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.theme_import_title),
                    icon = Icons.Rounded.ArrowCircleDown,
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )
                Preference(
                    title = stringResource(R.string.theme_export_title),
                    icon = Icons.Rounded.ArrowCircleUp,
                    onClick = {
                        navController?.navigate("settings/appearance/export")
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