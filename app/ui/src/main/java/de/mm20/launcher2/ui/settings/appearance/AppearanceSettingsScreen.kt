package de.mm20.launcher2.ui.settings.appearance

import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.IconSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarStyle
import de.mm20.launcher2.preferences.Settings.SystemBarsSettings.SystemBarColors
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.getShape
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.label
import de.mm20.launcher2.ui.component.preferences.value
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.theme.getTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val navController = LocalNavController.current
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(id = R.string.preference_layout),
                    summary = stringResource(id = R.string.preference_layout_summary),
                    onClick = {
                        navController?.navigate("settings/appearance/layout")
                    }
                )
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
                Preference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    summary = when (colorScheme) {
                        ColorScheme.Default -> stringResource(R.string.preference_colors_default)
                        ColorScheme.BlackAndWhite -> stringResource(R.string.preference_colors_bw)
                        ColorScheme.Custom -> stringResource(R.string.preference_colors_custom)
                        else -> null
                    },
                    onClick = {
                        navController?.navigate("settings/appearance/colorscheme")
                    }
                )
                val font by viewModel.font.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_font),
                    items = listOf(
                        "Outfit" to AppearanceSettings.Font.Outfit,
                        stringResource(R.string.preference_font_system) to AppearanceSettings.Font.SystemDefault,
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
                    }
                )

                Preference(
                    title = stringResource(R.string.preference_cards),
                    summary = stringResource(R.string.preference_cards_summary),
                    onClick = {
                        navController?.navigate("settings/appearance/cards")
                    }
                )
            }
            PreferenceCategory(title = stringResource(R.string.preference_category_grid)) {
                val iconSize by viewModel.iconSize.observeAsState(48)
                SliderPreference(
                    title = stringResource(R.string.preference_grid_icon_size),
                    value = iconSize,
                    step = 8,
                    min = 32,
                    max = 64,
                    onValueChanged = {
                        viewModel.setIconSize(it)
                    }
                )
                val showLabels by viewModel.showLabels.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_labels),
                    summary = stringResource(R.string.preference_grid_labels_summary),
                    value = showLabels == true,
                    onValueChanged = {
                        viewModel.setShowLabels(it)
                    }
                )
                val columnCount by viewModel.columnCount.observeAsState(5)
                SliderPreference(
                    title = stringResource(R.string.preference_grid_column_count),
                    value = columnCount,
                    min = 3,
                    max = 8,
                    onValueChanged = {
                        viewModel.setColumnCount(it)
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
                val isBlurSupported = remember { viewModel.isBlurAvailable(context) }
                if (isBlurSupported) {
                    val blurWallpaper by viewModel.blurWallpaper.observeAsState()
                    SwitchPreference(
                        title = stringResource(R.string.preference_blur_wallpaper),
                        summary = stringResource(
                            if (isBlurSupported) R.string.preference_blur_wallpaper_summary
                            else R.string.preference_blur_wallpaper_unsupported
                        ),
                        value = blurWallpaper == true && isBlurSupported,
                        onValueChanged = {
                            viewModel.setBlurWallpaper(it)
                        },
                        enabled = isBlurSupported
                    )
                }
            }
            PreferenceCategory(stringResource(R.string.preference_category_icons)) {
                val iconShape by viewModel.iconShape.observeAsState(IconSettings.IconShape.PlatformDefault)
                IconShapePreference(
                    title = stringResource(R.string.preference_icon_shape),
                    summary = getShapeName(iconShape),
                    value = iconShape,
                    onValueChanged = {
                        viewModel.setIconShape(it)
                    }
                )
                val adaptifyLegacyIcons by viewModel.adaptifyLegacyIcons.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_enforce_icon_shape),
                    summary = stringResource(R.string.preference_enforce_icon_shape_summary),
                    value = adaptifyLegacyIcons == true,
                    onValueChanged = {
                        viewModel.setAdaptifyLegacyIcons(it)
                    }
                )
                val themedIcons by viewModel.themedIcons.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_themed_icons),
                    summary = stringResource(R.string.preference_themed_icons_summary),
                    value = themedIcons == true,
                    onValueChanged = {
                        viewModel.setThemedIcons(it)
                    }
                )
                val forceThemedIcons by viewModel.forceThemedIcons.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_force_themed_icons),
                    summary = stringResource(R.string.preference_force_themed_icons_summary),
                    value = forceThemedIcons == true,
                    enabled = themedIcons == true,
                    onValueChanged = {
                        viewModel.setForceThemedIcons(it)
                    }
                )

                val iconPackPackage by viewModel.iconPack.observeAsState()
                val iconPackThemed by viewModel.iconPackThemed.collectAsState(true)
                val installedIconPacks by viewModel.installedIconPacks.collectAsState(emptyList())
                val iconPack by remember {
                    derivedStateOf { installedIconPacks.firstOrNull { it.packageName == iconPackPackage } }
                }
                val items = installedIconPacks.map {
                    it.name to it
                }
                Row(
                    verticalAlignment = (Alignment.CenterVertically)
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        ListPreference(
                            title = stringResource(R.string.preference_icon_pack),
                            items = items,
                            summary = if (items.size <= 1) {
                                stringResource(R.string.preference_icon_pack_summary_empty)
                            } else {
                                iconPack?.name ?: "System"
                            },
                            enabled = installedIconPacks.size > 1,
                            value = iconPack,
                            onValueChanged = {
                                if (it != null) viewModel.setIconPack(it.packageName)
                            },
                            itemLabel = {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = it.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (it.value?.themed == true) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .padding(end = 4.dp),
                                                    imageVector = Icons.Rounded.FormatPaint,
                                                    contentDescription = null,
                                                )
                                                Text(
                                                    text = stringResource(R.string.icon_pack_dynamic_colors),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }

                                }
                            }
                        )
                    }
                    if (iconPack?.themed == true) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp)
                                .alpha(0.38f)
                                .background(LocalContentColor.current)
                        )
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                        ) {
                            PlainTooltipBox(tooltip = { Text(stringResource(R.string.icon_pack_dynamic_colors)) }) {
                                FilledIconToggleButton(
                                    modifier = Modifier.tooltipAnchor(),
                                    checked = iconPackThemed,
                                    onCheckedChange = {
                                        viewModel.setIconPackThemed(it)
                                    }) {
                                    Icon(
                                        Icons.Rounded.FormatPaint,
                                        stringResource(R.string.icon_pack_dynamic_colors)
                                    )
                                }
                            }
                        }
                    }
                }
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
                AnimatedVisibility(searchBarStyle == SearchBarStyle.Transparent) {
                    val searchBarColor by viewModel.searchBarColor.observeAsState()
                    ListPreference(
                        title = stringResource(R.string.preference_search_bar_color),
                        value = searchBarColor,
                        items = listOf(
                            stringResource(R.string.preference_system_bar_icons_auto) to SearchBarColors.Auto,
                            stringResource(R.string.preference_system_bar_icons_light) to SearchBarColors.Light,
                            stringResource(R.string.preference_system_bar_icons_dark) to SearchBarColors.Dark,
                        ),
                        onValueChanged = {
                            if (it != null) viewModel.setSearchBarColor(it)
                        }
                    )
                }
            }
            PreferenceCategory(stringResource(R.string.preference_category_system_bars)) {
                val lightStatusBar by viewModel.statusBarIcons.observeAsState()
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
                val lightNavBar by viewModel.navBarIcons.observeAsState()
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
                val hideStatusBar by viewModel.hideStatusBar.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_hide_status_bar),
                    value = hideStatusBar == true,
                    onValueChanged = {
                        viewModel.setHideStatusBar(it)
                    }
                )
                val hideNavBar by viewModel.hideNavBar.observeAsState()
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
    value: SearchBarSettings.SearchBarStyle?,
    onValueChanged: (SearchBarSettings.SearchBarStyle) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })
    if (showDialog && value != null) {
        val styles = remember {
            SearchBarSettings.SearchBarStyle.values()
                .filter { it != SearchBarSettings.SearchBarStyle.UNRECOGNIZED }
        }
        val pagerState = rememberPagerState(styles.indexOf(value))

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
                        count = styles.size,
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
                    HorizontalPagerIndicator(pagerState = pagerState)
                }
            }
        )
    }
}

@Composable
fun IconShapePreference(
    title: String,
    summary: String? = null,
    value: IconSettings.IconShape?,
    onValueChanged: (IconSettings.IconShape) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })

    if (showDialog && value != null) {
        val shapes = remember {
            IconSettings.IconShape.values()
                .filter { it != IconSettings.IconShape.UNRECOGNIZED && it != IconSettings.IconShape.EasterEgg }
        }
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                tonalElevation = 16.dp,
                shadowElevation = 16.dp,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp
                        )
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        items(shapes) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val context = LocalContext.current
                                ShapedLauncherIcon(
                                    size = 48.dp,
                                    icon = {
                                        StaticLauncherIcon(
                                            foregroundLayer = StaticIconLayer(
                                                icon = ContextCompat.getDrawable(
                                                    context,
                                                    R.mipmap.ic_launcher_foreground
                                                )!!,
                                                scale = 1.5f,
                                            ),
                                            backgroundLayer = StaticIconLayer(
                                                icon = ColorDrawable(
                                                    context.getColor(R.color.ic_launcher_background)
                                                )
                                            )
                                        )
                                    },
                                    onClick = {
                                        onValueChanged(it)
                                        showDialog = false
                                    },
                                    shape = getShape(it)
                                )
                                Text(
                                    getShapeName(it) ?: "",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun getShapeName(shape: IconSettings.IconShape?): String? {
    return stringResource(
        when (shape) {
            IconSettings.IconShape.Triangle -> R.string.preference_icon_shape_triangle
            IconSettings.IconShape.Hexagon -> R.string.preference_icon_shape_hexagon
            IconSettings.IconShape.RoundedSquare -> R.string.preference_icon_shape_rounded_square
            IconSettings.IconShape.Squircle -> R.string.preference_icon_shape_squircle
            IconSettings.IconShape.Square -> R.string.preference_icon_shape_square
            IconSettings.IconShape.Pentagon -> R.string.preference_icon_shape_pentagon
            IconSettings.IconShape.PlatformDefault -> R.string.preference_icon_shape_platform
            IconSettings.IconShape.Circle -> R.string.preference_icon_shape_circle
            IconSettings.IconShape.Teardrop -> R.string.preference_icon_shape_teardrop
            IconSettings.IconShape.Pebble -> R.string.preference_icon_shape_pebble
            else -> return null
        }
    )
}