package de.mm20.launcher2.ui.settings.appearance

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
            PreferenceCategory(stringResource(R.string.preference_category_searchbar)) {
                val searchBarStyle by viewModel.searchBarStyle.observeAsState()
                SearchBarStylePreference(
                    title = stringResource(R.string.preference_search_bar_style),
                    summary = stringResource(R.string.preference_search_bar_style_summary),
                    value = searchBarStyle,
                    onValueChanged = {
                        viewModel.setSearchBarStyle(it)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun SearchBarStylePreference(
    title: String,
    summary: String? = null,
    value: SearchBarSettings.SearchBarStyle?,
    onValueChanged: (SearchBarSettings.SearchBarStyle) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })
    if (showDialog && value != null) {
        val styles = remember {
            SearchBarSettings.SearchBarStyle.values().filter { it != SearchBarSettings.SearchBarStyle.UNRECOGNIZED }
        }
        val pagerState = rememberPagerState(styles.indexOf(value))

        var level by remember { mutableStateOf(SearchBarLevel.Resting) }
        var previewSearchValue by remember { mutableStateOf("") }
        LaunchedEffect(null) {
            while(isActive) {
                delay(2000)
                level = SearchBarLevel.Active
                delay(1000)
                previewSearchValue = "A"
                delay(100)
                previewSearchValue = "AB"
                delay(100)
                previewSearchValue = "ABC"
                delay(800)
                level = SearchBarLevel.Raised
                delay(2000)
                level = SearchBarLevel.Resting
                previewSearchValue = ""
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onValueChanged(styles[pagerState.currentPage])
                }) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },

            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        count = styles.size,
                        state = pagerState,
                        modifier = Modifier
                            .height(150.dp)
                            .padding(bottom = 16.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                    ) {
                        SearchBar(level = level, style = styles[it], websearches = emptyList(), value = previewSearchValue, onValueChange = {})
                    }
                    HorizontalPagerIndicator(pagerState = pagerState)
                }
            }
        )
    }
}