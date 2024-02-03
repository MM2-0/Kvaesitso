package de.mm20.launcher2.ui.settings.icons

import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.preferences.IconShape
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.IconSettingsData
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
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
import kotlinx.coroutines.launch

@Composable
fun IconsSettingsScreen() {
    val viewModel: IconsSettingsScreenVM = viewModel(factory = IconsSettingsScreenVM.Factory)
    val context = LocalContext.current

    val grid by viewModel.grid.collectAsStateWithLifecycle(GridSettings())
    val icons by viewModel.icons.collectAsStateWithLifecycle(null)
    val density = LocalDensity.current
    val iconShape by viewModel.iconShape.collectAsStateWithLifecycle(IconShape.PlatformDefault)

    val installedIconPacks by viewModel.installedIconPacks.collectAsState(emptyList())

    val hasNotificationsPermission by viewModel.hasNotificationsPermission.collectAsStateWithLifecycle(null)

    val notificationBadges by viewModel.notificationBadges.collectAsStateWithLifecycle(null)
    val cloudFileBadges by viewModel.cloudFileBadges.collectAsStateWithLifecycle(null)
    val suspendedAppBadges by viewModel.suspendedAppBadges.collectAsStateWithLifecycle(null)
    val shortcutBadges by viewModel.shortcutBadges.collectAsStateWithLifecycle(null)
    val pluginBadges by viewModel.pluginBadges.collectAsStateWithLifecycle(null)

    val previewIcons by remember(grid?.iconSize) {
        viewModel.getPreviewIcons(with(density) { grid.iconSize.dp.toPx() }.toInt())
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
                if (previewIcons.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
                        ) {
                            for (icon in previewIcons) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ShapedLauncherIcon(size = grid.iconSize.dp, icon = { icon })
                                }
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
                            val tooltipState = rememberTooltipState()
                            val scope = rememberCoroutineScope()
                            TooltipBox(
                                state = tooltipState,
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(stringResource(R.string.icon_pack_dynamic_colors))
                                    }
                                },
                            ) {
                                FilledIconToggleButton(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            scope.launch {
                                                tooltipState.show()
                                            }
                                        }
                                    ),
                                    checked = icons?.iconPackThemed == true,
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
        }
        item {
            PreferenceCategory(
                title = stringResource(R.string.preference_category_badges),
            ) {
                AnimatedVisibility(hasNotificationsPermission == false) {
                    MissingPermissionBanner(
                        text = stringResource(R.string.missing_permission_notification_badges),
                        onClick = {
                            viewModel.requestNotificationsPermission(context as AppCompatActivity)
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_notification_badges),
                    summary = stringResource(R.string.preference_notification_badges_summary),
                    enabled = hasNotificationsPermission != false,
                    value = notificationBadges == true && hasNotificationsPermission == true,
                    onValueChanged = {
                        viewModel.setNotifications(it)
                    }
                )
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
                                    modifier = Modifier.clickable {
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