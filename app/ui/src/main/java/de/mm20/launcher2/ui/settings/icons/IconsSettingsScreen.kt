package de.mm20.launcher2.ui.settings.icons

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.getShape
import de.mm20.launcher2.ui.component.preferences.GuardedPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.flow.Flow

@Composable
fun IconsSettingsScreen() {
    val viewModel: IconsSettingsScreenVM = viewModel(factory = IconsSettingsScreenVM.Factory)
    val context = LocalContext.current

    val grid by viewModel.grid.collectAsStateWithLifecycle(GridSettings())
    val icons by viewModel.icons.collectAsStateWithLifecycle(null)
    val density = LocalDensity.current
    val iconShape by viewModel.iconShape.collectAsStateWithLifecycle(IconShape.PlatformDefault)

    val installedIconPacks by viewModel.installedIconPacks.collectAsState(emptyList())

    var showIconPackSheet by remember { mutableStateOf(false) }

    val hasNotificationsPermission by viewModel.hasNotificationsPermission.collectAsStateWithLifecycle(
        null
    )

    val notificationBadges by viewModel.notificationBadges.collectAsStateWithLifecycle(null)
    val cloudFileBadges by viewModel.cloudFileBadges.collectAsStateWithLifecycle(null)
    val suspendedAppBadges by viewModel.suspendedAppBadges.collectAsStateWithLifecycle(null)
    val shortcutBadges by viewModel.shortcutBadges.collectAsStateWithLifecycle(null)
    val pluginBadges by viewModel.pluginBadges.collectAsStateWithLifecycle(null)

    val iconSize = with(density) { grid.iconSize.dp.toPx() }.toInt()

    val previewIcons = remember(iconSize) {
        viewModel.getPreviewIcons(iconSize)
    }.collectAsState(
        emptyList()
    )

    PreferenceScreen(title = stringResource(id = R.string.preference_screen_icons)) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_grid)) {
                SliderPreference(
                    title = stringResource(R.string.preference_grid_icon_size),
                    value = grid.iconSize,
                    step = 8,
                    min = 32,
                    max = 64,
                    onValueChanged = {
                        viewModel.setIconSize(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_labels),
                    summary = stringResource(R.string.preference_grid_labels_summary),
                    value = grid.showLabels,
                    onValueChanged = {
                        viewModel.setShowLabels(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_list_style),
                    summary = stringResource(R.string.preference_grid_list_style_summary),
                    value = grid.showList,
                    onValueChanged = {
                        viewModel.setShowList(it)
                    }
                )
                AnimatedVisibility(
                    grid.showList
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_grid_list_icons),
                        summary = stringResource(R.string.preference_grid_list_icons_summary),
                        value = grid.showListIcons,
                        onValueChanged = {
                            viewModel.setShowListIcons(it)
                        }
                    )
                }
                SliderPreference(
                    title = stringResource(R.string.preference_grid_column_count),
                    value = grid.columnCount,
                    min = 3,
                    max = 12,
                    onValueChanged = {
                        viewModel.setColumnCount(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_icons)) {
                if (previewIcons.value.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                MaterialTheme.shapes.extraSmall
                            )
                            .padding(vertical = 24.dp, horizontal = 8.dp)
                    ) {
                        for (icon in previewIcons.value) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                ShapedLauncherIcon(size = grid.iconSize.dp, icon = { icon })
                            }
                        }
                    }
                }
                IconShapePreference(
                    title = stringResource(R.string.preference_icon_shape),
                    summary = getShapeName(iconShape),
                    value = iconShape,
                    onValueChanged = {
                        viewModel.setIconShape(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_enforce_icon_shape),
                    summary = stringResource(R.string.preference_enforce_icon_shape_summary),
                    value = icons?.adaptify == true,
                    onValueChanged = {
                        viewModel.setAdaptifyLegacyIcons(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_themed_icons),
                    summary = stringResource(R.string.preference_themed_icons_summary),
                    value = icons?.themedIcons == true,
                    onValueChanged = {
                        viewModel.setThemedIcons(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_force_themed_icons),
                    summary = stringResource(R.string.preference_force_themed_icons_summary),
                    value = icons?.forceThemed == true,
                    enabled = icons?.themedIcons == true,
                    onValueChanged = {
                        viewModel.setForceThemedIcons(it)
                    }
                )
                val iconPack by remember {
                    derivedStateOf { installedIconPacks.firstOrNull { it.packageName == icons?.iconPack } }
                }
                val items = installedIconPacks.map {
                    it.name to it
                }
                Preference(
                    title = stringResource(R.string.preference_icon_pack),
                    summary = if (items.size <= 1) {
                        stringResource(R.string.preference_icon_pack_summary_empty)
                    } else {
                        iconPack?.name ?: "System"
                    },
                    enabled = installedIconPacks.size > 1,
                    onClick = {
                        showIconPackSheet = true
                    },
                )
            }
        }
        item {
            PreferenceCategory(
                title = stringResource(R.string.preference_category_badges),
            ) {
                GuardedPreference(
                    locked = hasNotificationsPermission == false,
                    description = stringResource(R.string.missing_permission_notification_badges),
                    onUnlock = {
                        viewModel.requestNotificationsPermission(context as AppCompatActivity)
                    }
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_notification_badges),
                        summary = stringResource(R.string.preference_notification_badges_summary),
                        enabled = hasNotificationsPermission != false,
                        value = notificationBadges == true && hasNotificationsPermission == true,
                        onValueChanged = {
                            viewModel.setNotifications(it)
                        }
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_cloud_badges),
                    summary = stringResource(R.string.preference_cloud_badges_summary),
                    value = cloudFileBadges == true,
                    onValueChanged = {
                        viewModel.setCloudFiles(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_suspended_badges),
                    summary = stringResource(R.string.preference_suspended_badges_summary),
                    value = suspendedAppBadges == true,
                    onValueChanged = {
                        viewModel.setSuspendedApps(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_shortcut_badges),
                    summary = stringResource(R.string.preference_shortcut_badges_summary),
                    value = shortcutBadges == true,
                    onValueChanged = {
                        viewModel.setShortcuts(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_plugin_badges),
                    summary = stringResource(R.string.preference_plugin_badges_summary),
                    value = pluginBadges == true,
                    onValueChanged = {
                        viewModel.setPluginBadges(it)
                    }
                )
            }
        }
    }

    if (showIconPackSheet) {
        val iconPackPreviewIcons = remember(installedIconPacks, iconSize) {

            installedIconPacks.associate {
                it.packageName to viewModel.getIconPackPreviewIcons(
                    context,
                    it,
                    grid.columnCount,
                    iconSize,
                    icons?.themedIcons == true
                )
            }
        }

        IconPackSelectorSheet(
            installedIconPacks,
            iconPackPreviewIcons = iconPackPreviewIcons,
            columns = grid.columnCount,
            onSelect = {
                viewModel.setIconPack(it.packageName)
                showIconPackSheet = false
            },
            onDismiss = { showIconPackSheet = false },
        )
    }
}

@Composable
private fun IconPackSelectorSheet(
    installedIconPacks: List<IconPack>,
    iconPackPreviewIcons: Map<String, Flow<List<LauncherIcon>>>,
    columns: Int,
    onSelect: (IconPack) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = it,
        ) {
            items(installedIconPacks.size) {

                val pack = installedIconPacks[it]


                if (it > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Column {


                    Column(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onSelect(pack)
                            }
                    ) {
                        val icons by iconPackPreviewIcons[pack.packageName]!!.collectAsState(null)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = pack.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                            )
                            if (pack.themed) {
                                Icon(
                                    modifier = Modifier
                                        .size(20.dp),
                                    painter = painterResource(R.drawable.palette_20px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 16.dp)
                                .height(48.dp)
                        ) {
                            if (icons == null) {
                                for (i in 0 until columns) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ShapedLauncherIcon(size = 48.dp, icon = { null })
                                    }
                                }
                            } else {
                                for (icon in icons!!) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ShapedLauncherIcon(size = 48.dp, icon = { icon })
                                    }
                                }
                                for (i in 0..<(columns - icons!!.size)) {
                                    Box(
                                        modifier = Modifier.weight(1f),
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


@Composable
fun IconShapePreference(
    title: String,
    summary: String? = null,
    value: IconShape?,
    onValueChanged: (IconShape) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })

    if (showDialog && value != null) {
        val shapes = remember {
            IconShape.entries
                .filter { it != IconShape.EasterEgg }
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
                                Box(
                                    modifier = Modifier
                                        .clip(getShape(it))
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            onValueChanged(it)
                                            showDialog = false
                                        }
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
private fun getShapeName(shape: IconShape?): String? {
    return stringResource(
        when (shape) {
            IconShape.Triangle -> R.string.preference_icon_shape_triangle
            IconShape.Hexagon -> R.string.preference_icon_shape_hexagon
            IconShape.RoundedSquare -> R.string.preference_icon_shape_rounded_square
            IconShape.Squircle -> R.string.preference_icon_shape_squircle
            IconShape.Square -> R.string.preference_icon_shape_square
            IconShape.Pentagon -> R.string.preference_icon_shape_pentagon
            IconShape.PlatformDefault -> R.string.preference_icon_shape_platform
            IconShape.Circle -> R.string.preference_icon_shape_circle
            IconShape.Teardrop -> R.string.preference_icon_shape_teardrop
            IconShape.Pebble -> R.string.preference_icon_shape_pebble
            else -> return null
        }
    )
}