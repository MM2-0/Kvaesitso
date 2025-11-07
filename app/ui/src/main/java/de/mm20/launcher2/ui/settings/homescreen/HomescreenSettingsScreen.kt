package de.mm20.launcher2.ui.settings.homescreen

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.widgets.clock.ConfigureClockWidgetSheet
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper

@Composable
fun HomescreenSettingsScreen() {
    val viewModel: HomescreenSettingsScreenVM =
        viewModel(factory = HomescreenSettingsScreenVM.Factory)

    val navController = LocalNavController.current

    val context = LocalContext.current

    val dock by viewModel.dock.collectAsStateWithLifecycle(null)
    val dockRows by viewModel.dockRows.collectAsStateWithLifecycle(1)
    val fixedRotation by viewModel.fixedRotation.collectAsStateWithLifecycle(null)
    val widgetsOnHomeScreen by viewModel.widgetsOnHomeScreen.collectAsStateWithLifecycle(null)
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
                        viewModel.showClockWidgetSheet = true
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_favorites_part),
                    summary = stringResource(R.string.preference_clockwidget_favorites_part_summary),
                    value = dock == true,
                    onValueChanged = {
                        viewModel.setDock(it)
                    },
                )
                AnimatedVisibility(dock == true) {
                    SliderPreference(
                        title = stringResource(R.string.preference_clockwidget_dock_rows),
                        value = dockRows,
                        min = 1,
                        max = 4,
                        onValueChanged = {
                            viewModel.setDockRows(it)
                        }
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_widgets_on_home_screen),
                    summary = stringResource(R.string.preference_widgets_on_home_screen_summary),
                    value = widgetsOnHomeScreen == true,
                    onValueChanged = {
                        viewModel.setWidgetsOnHomeScreen(it)
                    })
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
                    colors = searchBarColor,
                    onStyleChanged = {
                        viewModel.setSearchBarStyle(it)
                    },
                    onColorsChanged = {
                        viewModel.setSearchBarColor(it)
                    }
                )

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
                        stringResource(R.string.preference_system_bar_icons_auto) to SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to SystemBarColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLightStatusBar(it)
                    }
                )
                ListPreference(
                    title = stringResource(R.string.preference_nav_bar_icons),
                    value = lightNavBar,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to SystemBarColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to SystemBarColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to SystemBarColors.Dark,
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

    if (viewModel.showClockWidgetSheet) {
        ConfigureClockWidgetSheet(onDismiss = { viewModel.showClockWidgetSheet = false })
    }
}

@Composable
fun SearchBarStylePreference(
    title: String,
    summary: String? = null,
    value: SearchBarStyle?,
    colors: SearchBarColors?,
    onStyleChanged: (SearchBarStyle) -> Unit,
    onColorsChanged: (SearchBarColors) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })
    if (showDialog && value != null) {
        val styles = remember {
            SearchBarStyle.entries
        }

        val darkColors =
            LocalPreferDarkContentOverWallpaper.current && colors == SearchBarColors.Auto || colors == SearchBarColors.Dark

        BottomSheetDialog(
            onDismissRequest = {
                showDialog = false
            }
        ) {
            Box(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (style in styles) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        when (style) {
                                            SearchBarStyle.Transparent -> R.string.preference_search_bar_style_transparent
                                            SearchBarStyle.Solid -> R.string.preference_search_bar_style_solid
                                            SearchBarStyle.Hidden -> R.string.preference_search_bar_style_hidden
                                        }
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        onStyleChanged(style)
                                    }
                                ) {
                                    Icon(
                                        painterResource(
                                            if (style == value) R.drawable.check_circle_24px_filled
                                            else R.drawable.circle_24px
                                        ),
                                        contentDescription = null,
                                        tint = if (style == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .border(
                                        if (style == value) 4.dp else 2.dp,
                                        if (style == value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        when {
                                            style != SearchBarStyle.Transparent -> MaterialTheme.colorScheme.inverseSurface
                                            LocalDarkTheme.current != darkColors -> MaterialTheme.colorScheme.surfaceContainer
                                            else -> MaterialTheme.colorScheme.inverseSurface
                                        }
                                    )
                                    .height(IntrinsicSize.Min)
                            ) {
                                SearchBar(
                                    modifier = Modifier
                                        .padding(16.dp),
                                    level = SearchBarLevel.Resting,
                                    style = style,
                                    value = "",
                                    onValueChange = {},
                                    readOnly = true,
                                    darkColors = darkColors,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            onStyleChanged(style)
                                        }
                                )
                            }
                            if (style == SearchBarStyle.Transparent) {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                ) {
                                    SegmentedButton(
                                        selected = colors == SearchBarColors.Auto,
                                        onClick = {
                                            onColorsChanged(SearchBarColors.Auto)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = 0,
                                            count = 3
                                        ),
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.auto_awesome_20dp),
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                        )
                                    }
                                    SegmentedButton(
                                        selected = colors == SearchBarColors.Dark,
                                        onClick = {
                                            onColorsChanged(SearchBarColors.Dark)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = 1,
                                            count = 3
                                        ),
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.light_mode_20px),
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                        )
                                    }
                                    SegmentedButton(
                                        selected = colors == SearchBarColors.Light,
                                        onClick = {
                                            onColorsChanged(SearchBarColors.Light)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = 2,
                                            count = 3
                                        ),
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.dark_mode_20px),
                                            contentDescription = null,
                                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}