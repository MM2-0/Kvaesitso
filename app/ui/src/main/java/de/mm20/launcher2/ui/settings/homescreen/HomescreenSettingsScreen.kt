package de.mm20.launcher2.ui.settings.homescreen

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.HorizontalPagerIndicator
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HomescreenSettingsScreen() {
    val viewModel: HomescreenSettingsScreenVM =
        viewModel(factory = HomescreenSettingsScreenVM.Factory)

    val navController = LocalNavController.current

    val context = LocalContext.current

    val fixedRotation by viewModel.fixedRotation.collectAsStateWithLifecycle(null)
    val editButton by viewModel.widgetEditButton.collectAsStateWithLifecycle(null)
    val searchBarStyle by viewModel.searchBarStyle.collectAsStateWithLifecycle(null)
    val searchBarColor by viewModel.searchBarColor.collectAsStateWithLifecycle(null)
    val bottomSearchBar by viewModel.bottomSearchBar.collectAsStateWithLifecycle(null)
    val fixedSearchBar by viewModel.fixedSearchBar.collectAsStateWithLifecycle(null)
    val lightStatusBar by viewModel.statusBarIcons.collectAsStateWithLifecycle(null)
    val dimWallpaper by viewModel.dimWallpaper.collectAsStateWithLifecycle()
    val blurWallpaper by viewModel.blurWallpaper.collectAsStateWithLifecycle()
    val blurWallpaperRadius by viewModel.blurWallpaperRadius.collectAsStateWithLifecycle()
    val lightNavBar by viewModel.navBarIcons.collectAsStateWithLifecycle(null)
    val hideStatusBar by viewModel.hideStatusBar.collectAsStateWithLifecycle(null)
    val hideNavBar by viewModel.hideNavBar.collectAsStateWithLifecycle(null)
    val chargingAnimation by viewModel.chargingAnimation.collectAsStateWithLifecycle(null)

    PreferenceScreen(title = stringResource(id = R.string.preference_screen_homescreen)) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_layout_fixed_rotation),
                    summary = stringResource(R.string.preference_layout_fixed_rotation_summary),
                    value = fixedRotation == true,
                    onValueChanged = {
                        viewModel.setFixedRotation(it)
                    },
                )
            }
        }
        item {
            PreferenceCategory(
                title = stringResource(id = R.string.preference_category_widgets)
            ) {
                Preference(
                    title = stringResource(R.string.preference_screen_clockwidget),
                    summary = stringResource(R.string.preference_screen_clockwidget_summary),
                    onClick = {
                        navController?.navigate("settings/homescreen/clock")
                    }
                )
                SwitchPreference(
                    title = stringResource(id = R.string.preference_edit_button),
                    summary = stringResource(id = R.string.preference_widgets_edit_button_summary),
                    value = editButton == true,
                    onValueChanged = {
                        viewModel.setWidgetEditButton(it)
                    })
            }

        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_searchbar)) {
                SearchBarStylePreference(
                    title = stringResource(R.string.preference_search_bar_style),
                    summary = stringResource(R.string.preference_search_bar_style_summary),
                    value = searchBarStyle,
                    onValueChanged = {
                        viewModel.setSearchBarStyle(it)
                    }
                )
                AnimatedVisibility(searchBarStyle == Settings.SearchBarSettings.SearchBarStyle.Transparent) {
                    ListPreference(
                        title = stringResource(R.string.preference_search_bar_color),
                        value = searchBarColor,
                        items = listOf(
                            stringResource(R.string.preference_system_bar_icons_auto) to Settings.SearchBarSettings.SearchBarColors.Auto,
                            stringResource(R.string.preference_system_bar_icons_light) to Settings.SearchBarSettings.SearchBarColors.Light,
                            stringResource(R.string.preference_system_bar_icons_dark) to Settings.SearchBarSettings.SearchBarColors.Dark,
                        ),
                        onValueChanged = {
                            if (it != null) viewModel.setSearchBarColor(it)
                        }
                    )
                }

                ListPreference(
                    title = stringResource(R.string.preference_layout_search_bar_position),
                    items = listOf(
                        stringResource(R.string.search_bar_position_top) to false,
                        stringResource(R.string.search_bar_position_bottom) to true,
                    ),
                    value = bottomSearchBar,
                    onValueChanged = {
                        if (it != null) viewModel.setBottomSearchBar(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_layout_fixed_search_bar),
                    summary = stringResource(R.string.preference_layout_fixed_search_bar_summary),
                    value = fixedSearchBar == true,
                    onValueChanged = {
                        viewModel.setFixedSearchBar(it)
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(id = R.string.preference_category_wallpaper)) {
                Preference(
                    title = stringResource(R.string.wallpaper),
                    summary = stringResource(R.string.preference_wallpaper_summary),
                    onClick = {
                        viewModel.openWallpaperChooser(context as AppCompatActivity)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_dim_wallpaper),
                    summary = stringResource(R.string.preference_dim_wallpaper_summary),
                    value = dimWallpaper,
                    onValueChanged = {
                        viewModel.setDimWallpaper(it)
                    }
                )
                val isBlurSupported = remember { viewModel.isBlurAvailable(context) }
                SwitchPreference(
                    title = stringResource(R.string.preference_blur_wallpaper),
                    summary = stringResource(
                        if (isBlurSupported) R.string.preference_blur_wallpaper_summary
                        else R.string.preference_blur_wallpaper_unsupported
                    ),
                    value = blurWallpaper && isBlurSupported,
                    onValueChanged = {
                        viewModel.setBlurWallpaper(it)
                    },
                    enabled = isBlurSupported
                )
                AnimatedVisibility(blurWallpaper && isBlurSupported) {
                    SliderPreference(
                        title = stringResource(R.string.preference_blur_wallpaper_radius),
                        value = blurWallpaperRadius,
                        onValueChanged = {
                            viewModel.setBlurWallpaperRadius(it)
                        },
                        min = 4,
                        max = 64,
                        step = 4,
                    )
                }
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_animations)) {
                SwitchPreference(
                    title = stringResource(R.string.preference_charging_animation),
                    summary = stringResource(R.string.preference_charging_animation_summary),
                    value = chargingAnimation == true,
                    onValueChanged = {
                        viewModel.setChargingAnimation(it)
                    }
                )
            }
        }
        item {

            PreferenceCategory(stringResource(R.string.preference_category_system_bars)) {
                ListPreference(
                    title = stringResource(R.string.preference_status_bar_icons),
                    value = lightStatusBar,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to Settings.SystemBarsSettings.SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to Settings.SystemBarsSettings.SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to Settings.SystemBarsSettings.SystemBarColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLightStatusBar(it)
                    }
                )
                ListPreference(
                    title = stringResource(R.string.preference_nav_bar_icons),
                    value = lightNavBar,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to Settings.SystemBarsSettings.SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to Settings.SystemBarsSettings.SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to Settings.SystemBarsSettings.SystemBarColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLightNavBar(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_status_bar),
                    value = hideStatusBar == true,
                    onValueChanged = {
                        viewModel.setHideStatusBar(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_nav_bar),
                    value = hideNavBar == true,
                    onValueChanged = {
                        viewModel.setHideNavBar(it)
                    }
                )
            }
        }
    }
}

@Composable
fun SearchBarStylePreference(
    title: String,
    summary: String? = null,
    value: Settings.SearchBarSettings.SearchBarStyle?,
    onValueChanged: (Settings.SearchBarSettings.SearchBarStyle) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })
    if (showDialog && value != null) {
        val styles = remember {
            Settings.SearchBarSettings.SearchBarStyle.values()
                .filter { it != Settings.SearchBarSettings.SearchBarStyle.UNRECOGNIZED }
        }
        val pagerState = rememberPagerState(initialPage = styles.indexOf(value)) { styles.size }

        var level by remember { mutableStateOf(SearchBarLevel.Resting) }
        var previewSearchValue by remember { mutableStateOf("") }
        LaunchedEffect(null) {
            while (isActive) {
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
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        text = stringResource(android.R.string.cancel),
                    )
                }
            },

            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .height(150.dp)
                            .padding(bottom = 16.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                    ) {
                        SearchBar(
                            modifier = Modifier.padding(8.dp),
                            level = level,
                            style = styles[it],
                            value = previewSearchValue,
                            onValueChange = {}
                        )
                    }
                    HorizontalPagerIndicator(pagerState = pagerState, pageCount = styles.size)
                }
            }
        )
    }
}