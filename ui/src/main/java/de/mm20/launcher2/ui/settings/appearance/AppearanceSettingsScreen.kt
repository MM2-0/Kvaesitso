package de.mm20.launcher2.ui.settings.appearance

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val context = LocalContext.current
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                val theme by viewModel.theme.observeAsState()
                ListPreference(
                    title = stringResource(id = R.string.preference_theme),
                    items = listOf(
                        stringResource(id = R.string.preference_theme_system) to Theme.System,
                        stringResource(id = R.string.preference_theme_light) to Theme.Light,
                        stringResource(id = R.string.preference_theme_dark) to Theme.Dark,
                    ),
                    value = theme,
                    onValueChanged = { newValue ->
                        if (newValue == null) return@ListPreference
                        viewModel.setTheme(newValue)
                    }
                )
                val colorScheme by viewModel.colorScheme.observeAsState()
                ListPreference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    items = listOf(
                        stringResource(id = R.string.preference_colors_default) to ColorScheme.Default,
                        stringResource(id = R.string.preference_colors_bw) to ColorScheme.BlackAndWhite,
                    ),
                    value = colorScheme,
                    onValueChanged = { newValue ->
                        if (newValue == null) return@ListPreference
                        viewModel.setColorScheme(newValue)
                    }
                )
            }
            PreferenceCategory(title = stringResource(R.string.preference_category_grid)) {
                val columnCount by viewModel.columnCount.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_grid_column_count),
                    items = (3..8).map {
                        it.toString() to it
                    },
                    value = columnCount,
                    onValueChanged = {
                        if (it != null) viewModel.setColumnCount(it)
                    }
                )
            }

            PreferenceCategory(stringResource(id = R.string.preference_category_wallpaper)) {
                Preference(
                    title = stringResource(R.string.wallpaper),
                    summary = stringResource(R.string.preference_wallpaper_summary),
                    onClick = {
                        viewModel.openWallpaperChooser(context as AppCompatActivity)
                    }
                )
                val dimWallpaper by viewModel.dimWallpaper.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_dim_wallpaper),
                    summary = stringResource(R.string.preference_dim_wallpaper_summary),
                    value = dimWallpaper == true,
                    onValueChanged = {
                        viewModel.setDimWallpaper(it)
                    }
                )
            }
        }
    }
}