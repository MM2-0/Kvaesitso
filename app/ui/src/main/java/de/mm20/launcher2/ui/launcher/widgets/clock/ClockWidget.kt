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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlignVerticalBottom
import androidx.compose.material.icons.rounded.AlignVerticalCenter
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
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
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetAlignment
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetColors
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.AnalogClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.BinaryClock
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock1
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.DigitalClock2
import de.mm20.launcher2.ui.launcher.widgets.clock.clocks.OrbitClock
import de.mm20.launcher2.ui.launcher.widgets.clock.parts.PartProvider
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.settings.clockwidget.ClockWidgetSettingsScreenVM

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
) {
    val viewModel: ClockWidgetVM = viewModel()
    val context = LocalContext.current
    val layout by viewModel.layout.collectAsState()
    val clockStyle by viewModel.clockStyle.collectAsState()
    val color by viewModel.color.collectAsState()
    val alignment by viewModel.alignment.collectAsState()
    val time = LocalTime.current

    val contentColor =
        if (color == ClockWidgetColors.Auto && LocalPreferDarkContentOverWallpaper.current || color == ClockWidgetColors.Dark) {
            Color(0, 0, 0, 180)
        } else {
            Color.White
        }

    LaunchedEffect(time) {
        viewModel.updateTime(time)
    }

    val partProviders by remember { viewModel.getActiveParts(context) }.collectAsStateWithLifecycle(
        emptyList()
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
            Box(
                modifier = modifier,
                contentAlignment = when (alignment) {
                    ClockWidgetAlignment.Center -> Alignment.Center
                    ClockWidgetAlignment.Top -> Alignment.TopCenter
                    else -> Alignment.BottomCenter
                }
            ) {

                CompositionLocalProvider(
                    LocalContentColor provides contentColor
                ) {
                    if (layout == ClockWidgetLayout.Vertical) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.clickable(
                                    enabled = clockStyle != ClockStyle.EmptyClock,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    viewModel.launchClockApp(context)
                                }
                            ) {
                                Clock(clockStyle, ClockWidgetLayout.Vertical)
                            }

                            for (part in partProviders) {
                                DynamicZone(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    layout = ClockWidgetLayout.Vertical,
                                    provider = part,
                                )
                            }
                        }
                    }
                    if (layout == ClockWidgetLayout.Horizontal) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (partProviders.size > 1) {
                                HorizontalPager(
                                    state = rememberPagerState { 2 },
                                    beyondBoundsPageCount = 1,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    partProviders.getOrNull(it)?.let {
                                        DynamicZone(
                                            modifier = Modifier.fillMaxWidth(),
                                            layout = ClockWidgetLayout.Horizontal,
                                            provider = it,
                                        )
                                    }
                                }
                            } else if (partProviders.isNotEmpty()) {
                                DynamicZone(
                                    modifier = Modifier.weight(1f),
                                    layout = ClockWidgetLayout.Horizontal,
                                    provider = partProviders[0],
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .height(56.dp)
                                    .width(2.dp)
                                    .background(
                                        LocalContentColor.current
                                    ),
                            )
                            Box(
                                modifier = Modifier.clickable(
                                    enabled = clockStyle != ClockStyle.EmptyClock,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    viewModel.launchClockApp(context)
                                }
                            ) {
                                Clock(clockStyle, ClockWidgetLayout.Horizontal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Clock(
    style: ClockStyle?,
    layout: ClockWidgetLayout
) {
    val time = LocalTime.current
    when (style) {
        ClockStyle.DigitalClock1,
        ClockStyle.DigitalClock1_Outlined,
        ClockStyle.DigitalClock1_MDY,
        ClockStyle.DigitalClock1_OnePlus -> DigitalClock1(time, layout, style)

        ClockStyle.DigitalClock2 -> DigitalClock2(time, layout)
        ClockStyle.BinaryClock -> BinaryClock(time, layout)
        ClockStyle.AnalogClock -> AnalogClock(time, layout)
        ClockStyle.OrbitClock -> OrbitClock(time, layout)
        ClockStyle.EmptyClock -> {}
        else -> {}
    }
}

@Composable
fun DynamicZone(
    modifier: Modifier = Modifier,
    layout: ClockWidgetLayout,
    provider: PartProvider?,
) {
    Column(
        modifier = modifier
    ) {
        provider?.Component(layout)
    }
}

@Composable
fun ConfigureClockWidgetSheet(
    onDismiss: () -> Unit,
) {
    val viewModel: ClockWidgetSettingsScreenVM = viewModel()
    val layout by viewModel.layout.collectAsState()
    val color by viewModel.color.collectAsState()
    val style by viewModel.clockStyle.collectAsState()
    val fillHeight by viewModel.fillHeight.collectAsState()
    val alignment by viewModel.alignment.collectAsState()

    val date by viewModel.datePart.collectAsState()
    val favorites by viewModel.favoritesPart.collectAsState()
    val media by viewModel.musicPart.collectAsState()
    val alarm by viewModel.alarmPart.collectAsState()
    val battery by viewModel.batteryPart.collectAsState()


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
                    selected = layout == ClockWidgetLayout.Vertical,
                    onClick = {
                        viewModel.setLayout(ClockWidgetLayout.Vertical)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = layout == ClockWidgetLayout.Vertical,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HorizontalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.preference_clockwidget_layout_vertical))
                }
                SegmentedButton(
                    selected = layout == ClockWidgetLayout.Horizontal,
                    onClick = {
                        viewModel.setLayout(ClockWidgetLayout.Horizontal)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(
                            active = layout == ClockWidgetLayout.Horizontal,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerticalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.preference_clockwidget_layout_horizontal))
                }
            }

            if (color != null && layout != null) {
                WatchFaceSelector(
                    layout = layout!!,
                    colors = color!!,
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
            OutlinedCard(
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_date_part),
                        summary = stringResource(R.string.preference_clockwidget_date_part_summary),
                        icon = Icons.Rounded.Today,
                        value = date == true,
                        onValueChanged = {
                            viewModel.setDatePart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_favorites_part),
                        summary = stringResource(R.string.preference_clockwidget_favorites_part_summary),
                        icon = Icons.Rounded.Star,
                        value = favorites == true,
                        onValueChanged = {
                            viewModel.setFavoritesPart(it)
                        }
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
                        title = stringResource(R.string.preference_clockwidget_music_part),
                        summary = stringResource(R.string.preference_clockwidget_music_part_summary),
                        icon = Icons.Rounded.MusicNote,
                        value = media == true,
                        onValueChanged = {
                            viewModel.setMusicPart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_alarm_part),
                        summary = stringResource(R.string.preference_clockwidget_alarm_part_summary),
                        icon = Icons.Rounded.Alarm,
                        value = alarm == true,
                        onValueChanged = {
                            viewModel.setAlarmPart(it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.preference_clockwidget_battery_part),
                        summary = stringResource(R.string.preference_clockwidget_battery_part_summary),
                        icon = Icons.Rounded.BatteryFull,
                        value = battery == true,
                        onValueChanged = {
                            viewModel.setBatteryPart(it)
                        }
                    )
                }
            }
        }
    }
}
