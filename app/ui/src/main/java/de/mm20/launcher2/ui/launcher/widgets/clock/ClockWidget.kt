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
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.preferences.ClockWidgetAlignment
import de.mm20.launcher2.preferences.ClockWidgetColors
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
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
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreenVM
import de.mm20.launcher2.ui.utils.isTwentyFourHours
import org.koin.androidx.compose.inject

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
                                imageVector = Icons.Rounded.Tune,
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
    val context = LocalContext.current
    val clockSettings: ClockWidgetSettings by inject()
    val showSeconds by clockSettings.showSeconds.collectAsState(initial = false)
    val useThemeColor by clockSettings.useThemeColor.collectAsState(initial = false)
    val timeFormat by clockSettings.timeFormat.collectAsState(null)

    if (timeFormat == null) return

    val isTwentyFourHours = timeFormat!!.isTwentyFourHours(context)

    when (style) {
        is ClockWidgetStyle.Digital1 -> DigitalClock1(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Digital2 -> DigitalClock2(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Binary -> BinaryClock(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
            useThemeColor = useThemeColor,
            darkColors = darkColors,
        )

        is ClockWidgetStyle.Analog -> AnalogClock(
            time,
            compact,
            showSeconds,
            useThemeColor,
            darkColors
        )

        is ClockWidgetStyle.Orbit -> OrbitClock(
            time = time,
            compact = compact,
            showSeconds = showSeconds,
            twentyFourHours = isTwentyFourHours,
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
    val alignment by viewModel.alignment.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val timeFormat by viewModel.timeFormat.collectAsState()
    val useAccentColor by viewModel.useThemeColor.collectAsState()
    val parts by viewModel.parts.collectAsState()

    BottomSheetDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(it)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = compact == false,
                    onClick = {
                        viewModel.setCompact(false)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = compact == false,
                            activeContent = {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HorizontalSplit,
                                contentDescription = null,
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.preference_clockwidget_layout_vertical))
                }
                SegmentedButton(
                    selected = compact == true,
                    onClick = {
                        viewModel.setCompact(true)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = compact == true,
                            activeContent = {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerticalSplit,
                                contentDescription = null,
                            )
                        }
                    }
                ) {
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

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = color == ClockWidgetColors.Auto,
                    onClick = {
                        viewModel.setColor(ClockWidgetColors.Auto)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                    )
                }
                SegmentedButton(
                    selected = color == ClockWidgetColors.Dark,
                    onClick = {
                        viewModel.setColor(ClockWidgetColors.Dark)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                    )
                }
                SegmentedButton(
                    selected = color == ClockWidgetColors.Light,
                    onClick = {
                        viewModel.setColor(ClockWidgetColors.Light)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DarkMode,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
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
                        icon = Icons.Rounded.ColorLens,
                        value = useAccentColor,
                        onValueChanged = {
                            viewModel.setUseThemeColor(it)
                        }
                    )
                    AnimatedVisibility(compact == false && style !is ClockWidgetStyle.Custom) {
                        SwitchPreference(
                            title = stringResource(R.string.preference_clock_widget_show_seconds),
                            icon = Icons.Rounded.Timer,
                            value = showSeconds,
                            onValueChanged = {
                                viewModel.setShowSeconds(it)
                            }
                        )
                    }
                    AnimatedVisibility(
                        style !is ClockWidgetStyle.Analog &&
                                style !is ClockWidgetStyle.Custom &&
                                style !is ClockWidgetStyle.Empty
                    ) {
                        var showDropdown by remember { mutableStateOf(false) }
                        Preference(
                            title = stringResource(R.string.preference_clock_widget_time_format),
                            summary = when (timeFormat) {
                                TimeFormat.TwelveHour -> stringResource(R.string.preference_clock_widget_time_format_12h)
                                TimeFormat.TwentyFourHour -> stringResource(R.string.preference_clock_widget_time_format_24h)
                                TimeFormat.System -> stringResource(R.string.preference_clock_widget_time_format_system)
                            },
                            icon = Icons.Rounded.AccessTime,
                            onClick = {
                                showDropdown = true
                            }
                        )
                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.preference_clock_widget_time_format_system))
                                },
                                onClick = {
                                    viewModel.setTimeFormat(TimeFormat.System)
                                    showDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.preference_clock_widget_time_format_24h))
                                },
                                onClick = {
                                    viewModel.setTimeFormat(TimeFormat.TwentyFourHour)
                                    showDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.preference_clock_widget_time_format_12h))
                                },
                                onClick = {
                                    viewModel.setTimeFormat(TimeFormat.TwelveHour)
                                    showDropdown = false
                                }
                            )
                        }
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
                        icon = Icons.Rounded.Height,
                        value = fillHeight == true,
                        onValueChanged = {
                            viewModel.setFillHeight(it)
                        }
                    )
                    AnimatedVisibility(fillHeight == true) {
                        var showDropdown by remember { mutableStateOf(false) }
                        Preference(
                            title = stringResource(R.string.preference_clock_widget_alignment),
                            summary = when (alignment) {
                                ClockWidgetAlignment.Top -> stringResource(R.string.preference_clock_widget_alignment_top)
                                ClockWidgetAlignment.Center -> stringResource(R.string.preference_clock_widget_alignment_center)
                                else -> stringResource(R.string.preference_clock_widget_alignment_bottom)
                            },
                            icon = when (alignment) {
                                ClockWidgetAlignment.Top -> Icons.Rounded.AlignVerticalTop
                                ClockWidgetAlignment.Center -> Icons.Rounded.AlignVerticalCenter
                                else -> Icons.Rounded.AlignVerticalBottom
                            },
                            onClick = {
                                showDropdown = true
                            }
                        )
                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false }) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AlignVerticalTop,
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
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AlignVerticalCenter,
                                        null
                                    )
                                },
                                text = { Text(stringResource(R.string.preference_clock_widget_alignment_center)) },
                                onClick = {
                                    viewModel.setAlignment(ClockWidgetAlignment.Center)
                                    showDropdown = false
                                })
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AlignVerticalBottom,
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
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_date_part),
                        summary = stringResource(R.string.preference_clockwidget_date_part_summary),
                        icon = Icons.Rounded.Today,
                        value = parts?.date == true,
                        onValueChanged = {
                            viewModel.setDatePart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_music_part),
                        summary = stringResource(R.string.preference_clockwidget_music_part_summary),
                        icon = Icons.Rounded.MusicNote,
                        value = parts?.music == true,
                        onValueChanged = {
                            viewModel.setMusicPart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_alarm_part),
                        summary = stringResource(R.string.preference_clockwidget_alarm_part_summary),
                        icon = Icons.Rounded.Alarm,
                        value = parts?.alarm == true,
                        onValueChanged = {
                            viewModel.setAlarmPart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_battery_part),
                        summary = stringResource(R.string.preference_clockwidget_battery_part_summary),
                        icon = Icons.Rounded.BatteryFull,
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
