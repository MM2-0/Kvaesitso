package de.mm20.launcher2.ui.settings.clockwidget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.HorizontalPagerIndicator
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetColors
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.launcher.widgets.clock.Clock
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*

@Composable
fun ClockWidgetSettingsScreen() {
    val viewModel: ClockWidgetSettingsScreenVM = viewModel()
    PreferenceScreen(
        title = stringResource(R.string.preference_screen_clockwidget),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/widgets/clock"
    ) {
        item {
            PreferenceCategory {
                val layout by viewModel.layout.collectAsState()
                ListPreference(
                    title = stringResource(R.string.preference_clockwidget_layout),
                    value = layout,
                    items = listOf(
                        stringResource(R.string.preference_clockwidget_layout_vertical) to ClockWidgetLayout.Vertical,
                        stringResource(R.string.preference_clockwidget_layout_horizontal) to ClockWidgetLayout.Horizontal,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLayout(it)
                    }
                )
                val clockStyle by viewModel.clockStyle.collectAsState()
                ClockStylePreference(
                    layout = layout ?: ClockWidgetLayout.Vertical,
                    value = clockStyle,
                    onValueChanged = {
                        viewModel.setClockStyle(it)
                    }
                )
                val color by viewModel.color.collectAsState()
                ListPreference(
                    title = stringResource(R.string.preference_clock_widget_color),
                    value = color,
                    items = listOf(
                        stringResource(R.string.preference_system_bar_icons_auto) to ClockWidgetColors.Auto,
                        stringResource(R.string.preference_system_bar_icons_light) to ClockWidgetColors.Light,
                        stringResource(R.string.preference_system_bar_icons_dark) to ClockWidgetColors.Dark,
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setColor(it)
                    }
                )
                val fillHeight by viewModel.fillHeight.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clock_widget_fill_height),
                    summary = stringResource(R.string.preference_clock_widget_fill_height_summary),
                    value = fillHeight == true,
                    onValueChanged = { viewModel.setFillHeight(it) }
                )
            }
        }
        item {
            PreferenceCategory {
                val datePart by viewModel.datePart.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_date_part),
                    summary = stringResource(R.string.preference_clockwidget_date_part_summary),
                    icon = Icons.Rounded.Today,
                    value = datePart == true,
                    onValueChanged = {
                        viewModel.setDatePart(it)
                    },
                )
                val favoritesPart by viewModel.favoritesPart.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_favorites_part),
                    summary = stringResource(R.string.preference_clockwidget_favorites_part_summary),
                    icon = Icons.Rounded.Star,
                    value = favoritesPart == true,
                    onValueChanged = {
                        viewModel.setFavoritesPart(it)
                    },
                )
            }
        }
        item {
            PreferenceCategory {
                val musicPart by viewModel.musicPart.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_music_part),
                    summary = stringResource(R.string.preference_clockwidget_music_part_summary),
                    icon = Icons.Rounded.MusicNote,
                    value = musicPart == true,
                    onValueChanged = {
                        viewModel.setMusicPart(it)
                    }
                )
                val alarmPart by viewModel.alarmPart.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_alarm_part),
                    summary = stringResource(R.string.preference_clockwidget_alarm_part_summary),
                    icon = Icons.Rounded.Alarm,
                    value = alarmPart == true,
                    onValueChanged = {
                        viewModel.setAlarmPart(it)
                    }
                )
                val batteryPart by viewModel.batteryPart.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_battery_part),
                    summary = stringResource(R.string.preference_clockwidget_battery_part_summary),
                    icon = Icons.Rounded.BatteryFull,
                    value = batteryPart == true,
                    onValueChanged = {
                        viewModel.setBatteryPart(it)
                    }
                )
            }
        }
    }
}

@Composable
fun ClockStylePreference(
    layout: ClockWidgetLayout,
    value: ClockStyle?,
    onValueChanged: (ClockStyle) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = stringResource(R.string.preference_clock_widget_style),
        summary = stringResource(R.string.preference_clock_widget_style_summary),
        onClick = {
            showDialog = true
        }
    )
    if (showDialog && value != null) {
        val styles = remember {
            ClockStyle.values().filter { it != ClockStyle.UNRECOGNIZED }
        }
        val pagerState = rememberPagerState(styles.indexOf(value)) { styles.size }

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
                        modifier = Modifier.height(300.dp).fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Clock(
                                style = styles[it],
                                layout = layout
                            )
                        }
                    }
                    HorizontalPagerIndicator(pagerState = pagerState, pageCount = styles.size)
                }
            }
        )
    }
}