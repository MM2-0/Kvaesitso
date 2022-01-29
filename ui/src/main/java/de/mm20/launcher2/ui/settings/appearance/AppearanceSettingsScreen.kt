package de.mm20.launcher2.ui.settings.appearance

import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.IconSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
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
                val themedIcons by viewModel.themedIcons.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_themed_icons),
                    summary = stringResource(R.string.preference_themed_icons_summary),
                    value = themedIcons == true,
                    onValueChanged = {
                        viewModel.setThemedIcons(it)
                    }
                )

                val iconPack by viewModel.iconPack.observeAsState()
                val installedIconPacks by viewModel.installedIconPacks.observeAsState(emptyList())
                val items = installedIconPacks.map {
                    it.name to it.packageName
                }
                ListPreference(
                    title = stringResource(R.string.preference_icon_pack),
                    items = items,
                    summary = if (items.size <= 1) {
                        stringResource(R.string.preference_icon_pack_summary_empty)
                    } else {
                        items.firstOrNull { iconPack == it.value }?.label ?: "System"
                    },
                    enabled = installedIconPacks.size > 1,
                    value = iconPack,
                    onValueChanged = {
                        if (it != null) viewModel.setIconPack(it)
                    }
                )

                val legacyIconBackground by viewModel.legacyIconBackground.observeAsState()
                LegacyIconBackgroundPreference(
                    title = stringResource(R.string.preference_legacy_icon_bg),
                    summary = stringResource(R.string.preference_legacy_icon_bg_summary),
                    value = legacyIconBackground,
                    onValueChanged = {
                        viewModel.setLegacyIconBackground(it)
                    },
                    iconShape = iconShape
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
            PreferenceCategory(stringResource(R.string.preference_category_system_bars)) {
                val lightStatusBar by viewModel.lightStatusBar.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_light_status_bar),
                    value = lightStatusBar == true,
                    onValueChanged = {
                        viewModel.setLightStatusBar(it)
                    }
                )
                val lightNavBar by viewModel.lightNavBar.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_light_nav_bar),
                    value = lightNavBar == true,
                    onValueChanged = {
                        viewModel.setLightNavBar(it)
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
                        SearchBar(
                            level = level,
                            style = styles[it],
                            websearches = emptyList(),
                            value = previewSearchValue,
                            onValueChange = {})
                    }
                    HorizontalPagerIndicator(pagerState = pagerState)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
                shape = RoundedCornerShape(16.dp),
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
                        cells = GridCells.Adaptive(96.dp),
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
                                AndroidView(factory = { context ->
                                    LauncherIconView(context).apply {
                                        shape = it
                                        icon = LauncherIcon(
                                            foreground = AppCompatResources.getDrawable(
                                                context,
                                                R.mipmap.ic_launcher_foreground
                                            )!!,
                                            background = ColorDrawable(context.getColor(R.color.ic_launcher_background))
                                        )
                                        setOnClickListener { _ ->
                                            onValueChanged(it)
                                            showDialog = false
                                        }
                                        layoutParams = ViewGroup.LayoutParams(
                                            (48 * context.dp).toInt(),
                                            (48 * context.dp).toInt(),
                                        )
                                    }
                                })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LegacyIconBackgroundPreference(
    title: String,
    summary: String? = null,
    value: IconSettings.LegacyIconBackground?,
    onValueChanged: (IconSettings.LegacyIconBackground) -> Unit,
    iconShape: IconSettings.IconShape
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })

    if (showDialog && value != null) {
        val colors = remember {
            IconSettings.LegacyIconBackground.values()
                .filter { it != IconSettings.LegacyIconBackground.UNRECOGNIZED }
        }
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                tonalElevation = 16.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
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
                        cells = GridCells.Adaptive(96.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    ) {
                        items(colors) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AndroidView(factory = { context ->
                                    LauncherIconView(context).apply {
                                        shape = iconShape
                                        icon = LauncherIcon(
                                            foreground = AppCompatResources.getDrawable(
                                                context,
                                                R.mipmap.ic_launcher_foreground
                                            )!!,
                                            background = null,
                                            autoGenerateBackgroundMode = when (it) {
                                                IconSettings.LegacyIconBackground.Dynamic -> LauncherIcon.BACKGROUND_DYNAMIC
                                                IconSettings.LegacyIconBackground.None -> LauncherIcon.BACKGROUND_NONE
                                                else -> LauncherIcon.BACKGROUND_WHITE
                                            }
                                        )
                                        setOnClickListener { _ ->
                                            onValueChanged(it)
                                            showDialog = false
                                        }
                                        layoutParams = ViewGroup.LayoutParams(
                                            (48 * context.dp).toInt(),
                                            (48 * context.dp).toInt(),
                                        )
                                    }
                                })
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
            else -> return null
        }
    )
}