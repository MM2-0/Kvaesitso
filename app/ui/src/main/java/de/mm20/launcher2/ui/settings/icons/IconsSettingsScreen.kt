package de.mm20.launcher2.ui.settings.icons

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
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltipBox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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
import de.mm20.launcher2.preferences.Settings
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

@Composable
fun IconsSettingsScreen() {
    val viewModel: IconsSettingsScreenVM = viewModel(factory = IconsSettingsScreenVM.Factory)
    val context = LocalContext.current

    val iconSize by viewModel.iconSize.collectAsStateWithLifecycle(48)
    val showLabels by viewModel.showLabels.collectAsStateWithLifecycle(null)
    val columnCount by viewModel.columnCount.collectAsStateWithLifecycle(5)
    val iconShape by viewModel.iconShape.collectAsStateWithLifecycle(Settings.IconSettings.IconShape.PlatformDefault)
    val adaptifyLegacyIcons by viewModel.adaptifyLegacyIcons.collectAsStateWithLifecycle(null)
    val themedIcons by viewModel.themedIcons.collectAsStateWithLifecycle(null)

    val iconPackPackage by viewModel.iconPack.collectAsStateWithLifecycle(null)
    val iconPackThemed by viewModel.iconPackThemed.collectAsState(true)
    val installedIconPacks by viewModel.installedIconPacks.collectAsState(emptyList())
    val forceThemedIcons by viewModel.forceThemedIcons.collectAsStateWithLifecycle(null)

    val hasNotificationsPermission by viewModel.hasNotificationsPermission.collectAsStateWithLifecycle(null)

    val notificationBadges by viewModel.notificationBadges.collectAsStateWithLifecycle(null)
    val cloudFileBadges by viewModel.cloudFileBadges.collectAsStateWithLifecycle(null)
    val suspendedAppBadges by viewModel.suspendedAppBadges.collectAsStateWithLifecycle(null)
    val shortcutBadges by viewModel.shortcutBadges.collectAsStateWithLifecycle(null)
    
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_icons)) {
        item {
            PreferenceCategory(title = stringResource(R.string.preference_category_grid)) {
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
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_labels),
                    summary = stringResource(R.string.preference_grid_labels_summary),
                    value = showLabels == true,
                    onValueChanged = {
                        viewModel.setShowLabels(it)
                    }
                )
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
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_icons)) {
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
                    value = adaptifyLegacyIcons == true,
                    onValueChanged = {
                        viewModel.setAdaptifyLegacyIcons(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_themed_icons),
                    summary = stringResource(R.string.preference_themed_icons_summary),
                    value = themedIcons == true,
                    onValueChanged = {
                        viewModel.setThemedIcons(it)
                    }
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_force_themed_icons),
                    summary = stringResource(R.string.preference_force_themed_icons_summary),
                    value = forceThemedIcons == true,
                    enabled = themedIcons == true,
                    onValueChanged = {
                        viewModel.setForceThemedIcons(it)
                    }
                )
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
            }
        }
    }
}





@Composable
fun IconShapePreference(
    title: String,
    summary: String? = null,
    value: Settings.IconSettings.IconShape?,
    onValueChanged: (Settings.IconSettings.IconShape) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { showDialog = true })

    if (showDialog && value != null) {
        val shapes = remember {
            Settings.IconSettings.IconShape.values()
                .filter { it != Settings.IconSettings.IconShape.UNRECOGNIZED && it != Settings.IconSettings.IconShape.EasterEgg }
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
private fun getShapeName(shape: Settings.IconSettings.IconShape?): String? {
    return stringResource(
        when (shape) {
            Settings.IconSettings.IconShape.Triangle -> R.string.preference_icon_shape_triangle
            Settings.IconSettings.IconShape.Hexagon -> R.string.preference_icon_shape_hexagon
            Settings.IconSettings.IconShape.RoundedSquare -> R.string.preference_icon_shape_rounded_square
            Settings.IconSettings.IconShape.Squircle -> R.string.preference_icon_shape_squircle
            Settings.IconSettings.IconShape.Square -> R.string.preference_icon_shape_square
            Settings.IconSettings.IconShape.Pentagon -> R.string.preference_icon_shape_pentagon
            Settings.IconSettings.IconShape.PlatformDefault -> R.string.preference_icon_shape_platform
            Settings.IconSettings.IconShape.Circle -> R.string.preference_icon_shape_circle
            Settings.IconSettings.IconShape.Teardrop -> R.string.preference_icon_shape_teardrop
            Settings.IconSettings.IconShape.Pebble -> R.string.preference_icon_shape_pebble
            else -> return null
        }
    )
}