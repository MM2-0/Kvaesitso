package de.mm20.launcher2.ui.launcher.widgets.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlignVerticalBottom
import androidx.compose.material.icons.rounded.AlignVerticalCenter
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.AnalogClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.BinaryClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.CustomClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock1
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock2
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.OrbitClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.SegmentClock
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreenVM
import de.mm20.launcher2.ui.utils.isTwentyFourHours
import org.koin.compose.koinInject

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    fillScreenHeight: Boolean,
    editMode: Boolean = false,
) {
    val viewModel: ClockWidgetVM = viewModel()
    val context = LocalContext.current
    val compact by viewModel.compactLayout.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val color by viewModel.color.collectAsState()
    val alignment by viewModel.alignment.collectAsState()
    val time = LocalTime.current

    val darkColors =
        color == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current || color == ClockWidgetColors.Dark

    val contentColor =
        if (darkColors) {
            Color(0, 0, 0, 180)
        } else {
            Color.White
        }

    LaunchedEffect(time) {
        viewModel.updateTime(time)
    }

    val partProvider by remember { viewModel.getActivePart(context) }.collectAsStateWithLifecycle(
        null
    )

    AnimatedContent(editMode, label = "ClockWidget") {
        if (it) {
            var configure by remember { mutableStateOf(false) }
            Column {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 2.dp,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(id = R.string.preference_screen_clockwidget),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                        IconButton(onClick = {
                            configure = true
                        }) {
                            Icon(
                                painterResource(R.drawable.tune_24px),
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                }
                HorizontalDivider()
                if (configure) {
                    ConfigureClockWidgetSheet(onDismiss = { configure = false })
                }
            }
        } else {
            Column(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .then(if (fillScreenHeight) Modifier.weight(1f) else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = if (compact == true) 0.dp else 24.dp),
                    contentAlignment = when (alignment) {
                        ClockWidgetAlignment.Center -> Alignment.Center
                        ClockWidgetAlignment.Top -> Alignment.TopCenter
                        else -> Alignment.BottomCenter
                    }
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides contentColor
                    ) {
                        if (compact == false) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier.clickable(
                                        enabled = clockStyle !is ClockWidgetStyle.Empty,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        viewModel.launchClockApp(context)
                                    }
                                ) {
                                    Clock(clockStyle, false, darkColors)
                                }

                                if (partProvider != null) {
                                    DynamicZone(
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        compact = false,
                                        provider = partProvider,
                                    )
                                }
                            }
                        }
                        if (compact == true) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (partProvider != null) {
                                    DynamicZone(
                                        modifier = Modifier.weight(1f),
                                        compact = true,
                                        provider = partProvider,
                                    )
                                }
                                if (clockStyle !is ClockWidgetStyle.Empty) {
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .height(56.dp)
                                            .width(2.dp)
                                            .background(
                                                LocalContentColor.current
                                            ),
                                    )
                                }
                                Box(
                                    modifier = Modifier.clickable(
                                        enabled = clockStyle !is ClockWidgetStyle.Empty,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        viewModel.launchClockApp(context)
                                    }
                                ) {
                                    Clock(clockStyle, true, darkColors)
                                }
                            }
                        }
                    }
                }
                val dockProvider by viewModel.dockProvider.collectAsState()
                if (dockProvider != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        dockProvider?.Component(false)
                    }
                }
            }
        }
    }
}

/**
 * @param darkColors: use dark content color / suited for light backgrounds
 */
@Composable
fun Clock(
    style: ClockWidgetStyle?,
    compact: Boolean,
    darkColors: Boolean = false
) {
    val time = LocalTime.current
    val timeFormat = LocalTimeFormat.current
    val context = LocalContext.current
    val clockSettings: ClockWidgetSettings = koinInject()
    val showSeconds by clockSettings.showSeconds.collectAsState(initial = false)
    val useEightBits by clockSettings.useEightBits.collectAsState(initial = false)
    val monospaced by clockSettings.monospaced.collectAsState(initial = false)
    val useThemeColor by clockSettings.useThemeColor.collectAsState(initial = false)


    val isTwentyFourHours = timeFormat.isTwentyFourHours(context)

    when (style) {
        is ClockWidgetStyle.Digital1 -> DigitalClock1(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            monospaced = monospaced,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
            style = style,
        )

        is ClockWidgetStyle.Digital2 -> DigitalClock2(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            monospaced = monospaced,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Binary -> BinaryClock(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            useEightBits = useEightBits,
            twentyFourHours = isTwentyFourHours,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Analog -> AnalogClock(
            time,
            compact,
            showSeconds,
            useThemeColor,
            darkColors,
            style
        )

        is ClockWidgetStyle.Orbit -> OrbitClock(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            monospaced = monospaced,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Segment -> SegmentClock(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Custom -> CustomClock(style, compact, useThemeColor, darkColors)
        is ClockWidgetStyle.Empty -> {}
        else -> {}
    }
}

@Composable
fun DynamicZone(
    modifier: Modifier = Modifier,
    compact: Boolean,
    provider: PartProvider?,
) {
    Column(
        modifier = modifier
    ) {
        provider?.Component(compact)
    }
}

@Composable
fun ConfigureClockWidgetSheet(
    onDismiss: () -> Unit,
) {
    val viewModel: ClockWidgetSettingsScreenVM = viewModel()
    val compact by viewModel.compact.collectAsState()
    val color by viewModel.color.collectAsState()
    val style by viewModel.clockStyle.collectAsState()
    val fillHeight by viewModel.fillHeight.collectAsState()
    val widgetsOnHome by viewModel.widgetsOnHome.collectAsState()
    val alignment by viewModel.alignment.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val useEightBits by viewModel.useEightBits.collectAsState()
    val monospaced by viewModel.monospaced.collectAsState()
    val useAccentColor by viewModel.useThemeColor.collectAsState()
    val parts by viewModel.parts.collectAsState()
    val smartspacer by viewModel.useSmartspacer.collectAsState()

    BottomSheetDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(it)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = compact == false,
                    onCheckedChange = {
                        if (it) viewModel.setCompact(false)
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Icon(
                        painterResource(if (compact == false) R.drawable.check_20px else R.drawable.splitscreen_top_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ToggleButtonDefaults.IconSpacing)
                            .size(ToggleButtonDefaults.IconSize)
                    )
                    Text(text = stringResource(R.string.preference_clockwidget_layout_vertical))
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = compact == true,
                    onCheckedChange = {
                        if (it) viewModel.setCompact(true)
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Icon(
                        painterResource(if (compact == true) R.drawable.check_20px else R.drawable.splitscreen_right_20px),
                        null,
                        modifier = Modifier
                            .padding(end = ToggleButtonDefaults.IconSpacing)
                            .size(ToggleButtonDefaults.IconSize)
                    )
                    Text(text = stringResource(R.string.preference_clockwidget_layout_horizontal))
                }
            }

            val availableStyles by viewModel.availableClockStyles.collectAsState()

            if (color != null && compact != null && availableStyles.isNotEmpty()) {
                WatchFaceSelector(
                    styles = availableStyles,
                    compact = compact!!,
                    colors = color!!,
                    themeColors = useAccentColor,
                    selected = style,
                    onSelect = {
                        viewModel.setClockStyle(it)
                    })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Auto,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Auto)
                    },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                ) {
                    Icon(
                        painterResource(R.drawable.auto_awesome_24dp),
                        contentDescription = null,
                    )
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Dark,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Dark)
                    },
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                ) {
                    Icon(
                        painterResource(R.drawable.light_mode_24px),
                        contentDescription = null,
                    )
                }
                ToggleButton(
                    modifier = Modifier.weight(1f),
                    checked = color == ClockWidgetColors.Light,
                    onCheckedChange = {
                        if (it) viewModel.setColor(ClockWidgetColors.Light)
                    },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                ) {
                    Icon(
                        painterResource(R.drawable.dark_mode_24px),
                        contentDescription = null,
                    )
                }
            }
            OutlinedCard(
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.widget_use_theme_colors),
                        icon = R.drawable.palette_24px,
                        value = useAccentColor,
                        onValueChanged = {
                            viewModel.setUseThemeColor(it)
                        }
                    )
                    AnimatedVisibility(compact == false && style !is ClockWidgetStyle.Custom) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_clock_widget_show_seconds),
                            icon = R.drawable.timer_24px,
                            value = showSeconds,
                            onValueChanged = {
                                viewModel.setShowSeconds(it)
                            }
                        )
                    }
                    AnimatedVisibility(compact == false && style is ClockWidgetStyle.Binary) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_clock_widget_use_eight_bits),
                            icon = Icons.Rounded.Build,
                            value = useEightBits,
                            onValueChanged = {
                                viewModel.setUseEightBits(it)
                            }
                        )
                    }
                    AnimatedVisibility(
                        style is ClockWidgetStyle.Digital1 ||
                                style is ClockWidgetStyle.Digital2 ||
                                style is ClockWidgetStyle.Orbit
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_clock_widget_monospaced),
                            icon = R.drawable._123_24px,
                            value = monospaced,
                            onValueChanged = {
                                viewModel.setMonospaced(it)
                            }
                        )
                    }
                }
            }
            OutlinedCard(
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_clock_widget_fill_height),
                        icon = R.drawable.fit_page_height_24px,
                        value = fillHeight == true || widgetsOnHome == false,
                        onValueChanged = {
                            viewModel.setFillHeight(it)
                        },
                        enabled = widgetsOnHome == true,
                    )
                    var showDropdown by remember { mutableStateOf(false) }
                    Preference(
                        title = stringResource(R.string.preference_clock_widget_alignment),
                        summary = when (alignment) {
                            ClockWidgetAlignment.Top -> stringResource(R.string.preference_clock_widget_alignment_top)
                            ClockWidgetAlignment.Center -> stringResource(R.string.preference_clock_widget_alignment_center)
                            else -> stringResource(R.string.preference_clock_widget_alignment_bottom)
                        },
                        icon = when (alignment) {
                            ClockWidgetAlignment.Top -> R.drawable.align_vertical_top_24px
                            ClockWidgetAlignment.Center -> R.drawable.align_vertical_center_24px
                            else -> R.drawable.align_vertical_bottom_24px
                        },
                        onClick = {
                            showDropdown = true
                        },
                        enabled = fillHeight == true,
                    )
                    DropdownMenuPopup(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShapes()
                        ) {
                            DropdownMenuItem(
                                shapes = MenuDefaults.itemShape(0, 3),
                                selected = alignment == ClockWidgetAlignment.Top,
                                checkedLeadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.align_vertical_top_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(R.string.preference_clock_widget_alignment_top)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Top)
                                    showDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                shapes = MenuDefaults.itemShape(1, 3),
                                selected = alignment == ClockWidgetAlignment.Center,
                                checkedLeadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.align_vertical_center_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(R.string.preference_clock_widget_alignment_center)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Center)
                                    showDropdown = false
                                })
                            DropdownMenuItem(
                                shapes = MenuDefaults.itemShape(2, 3),
                                selected = alignment == ClockWidgetAlignment.Bottom,
                                checkedLeadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.check_24px),
                                        null
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.align_vertical_bottom_24px),
                                        null
                                    )
                                },
                                text = { Text(stringResource(R.string.preference_clock_widget_alignment_bottom)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Bottom)
                                    showDropdown = false
                                })
                        }
                    }
                }
            }
            Text(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                text = stringResource(R.string.preference_clockwidget_dynamic_zone)
            )
            OutlinedCard(
                modifier = Modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (smartspacer == true) {
                        Banner(
                            modifier = Modifier.padding(16.dp),
                            text = stringResource(R.string.preference_clockwidget_smartspacer),
                            icon = R.drawable.info_24px,
                            primaryAction = {
                                Button(
                                    onClick = {
                                        viewModel.disableSmartspacer()
                                    }
                                ) {
                                    Text(stringResource(R.string.turn_off))
                                }
                            }
                        )
                    }
                    if (smartspacer == false) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_clockwidget_date_part),
                            summary = stringResource(R.string.preference_clockwidget_date_part_summary),
                            icon = R.drawable.today_24px,
                            value = parts?.date == true,
                            onValueChanged = {
                                viewModel.setDatePart(it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.preference_clockwidget_music_part),
                            summary = stringResource(R.string.preference_clockwidget_music_part_summary),
                            icon = R.drawable.music_note_24px,
                            value = parts?.music == true,
                            onValueChanged = {
                                viewModel.setMusicPart(it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.preference_clockwidget_alarm_part),
                            summary = stringResource(R.string.preference_clockwidget_alarm_part_summary),
                            icon = R.drawable.alarm_24px,
                            value = parts?.alarm == true,
                            onValueChanged = {
                                viewModel.setAlarmPart(it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(R.string.preference_clockwidget_battery_part),
                            summary = stringResource(R.string.preference_clockwidget_battery_part_summary),
                            icon = R.drawable.battery_full_24px,
                            value = parts?.battery == true,
                            onValueChanged = {
                                viewModel.setBatteryPart(it)
                            }
                        )
                    }
                }
            }
        }
    }
}
