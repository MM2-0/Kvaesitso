package de.mm20.launcher2.ui.settings.clockwidget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockStyle
import de.mm20.launcher2.preferences.Settings.ClockWidgetSettings.ClockWidgetLayout
import de.mm20.launcher2.ui.Clock
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.*

@Composable
fun ClockWidgetSettingsScreen() {
    val viewModel: ClockWidgetSettingsScreenVM = viewModel()
    PreferenceScreen(
        title = stringResource(R.string.preference_screen_clockwidget)
    ) {
        item {
            PreferenceCategory {
                val layout by viewModel.layout.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_clockwidget_layout),
                    value = layout,
                    items = listOf(
                        stringResource(R.string.preference_clockwidget_layout_vertical) to ClockWidgetLayout.Vertical,
                        stringResource(R.string.preference_clockwidget_layout_horizontal) to ClockWidgetLayout.Horizontal
                    ),
                    onValueChanged = {
                        if (it != null) viewModel.setLayout(it)
                    }
                )
                val clockStyle by viewModel.clockStyle.observeAsState()
                ClockStylePreference(
                    layout = layout ?: ClockWidgetLayout.Vertical,
                    value = clockStyle,
                    onValueChanged = {
                        viewModel.setClockStyle(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                val datePart by viewModel.datePart.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_date_part),
                    summary = stringResource(R.string.preference_clockwidget_date_part_summary),
                    icon = Icons.Rounded.CalendarToday,
                    value = datePart == true,
                    onValueChanged = {
                        viewModel.setDatePart(it)
                    }
                )
                val favoritesPart by viewModel.favoritesPart.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_favorites_part),
                    summary = stringResource(R.string.preference_clockwidget_favorites_part_summary),
                    icon = Icons.Rounded.Star,
                    value = favoritesPart == true,
                    onValueChanged = {
                        viewModel.setFavoritesPart(it)
                    }
                )
            }
        }
        item {
            PreferenceCategory {
                val musicPart by viewModel.musicPart.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_music_part),
                    summary = stringResource(R.string.preference_clockwidget_music_part_summary),
                    icon = Icons.Rounded.MusicNote,
                    value = musicPart == true,
                    onValueChanged = {
                        viewModel.setMusicPart(it)
                    }
                )
                val alarmPart by viewModel.alarmPart.observeAsState()
                SwitchPreference(
                    title = stringResource(R.string.preference_clockwidget_alarm_part),
                    summary = stringResource(R.string.preference_clockwidget_alarm_part_summary),
                    icon = Icons.Rounded.Alarm,
                    value = alarmPart == true,
                    onValueChanged = {
                        viewModel.setAlarmPart(it)
                    }
                )
                val batteryPart by viewModel.batteryPart.observeAsState()
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

@OptIn(ExperimentalPagerApi::class)
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
        val pagerState = rememberPagerState(styles.indexOf(value))

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
                        modifier = Modifier.height(300.dp)
                    ) {
                        Clock(
                            style = styles[it],
                            layout = layout
                        )
                    }
                    HorizontalPagerIndicator(pagerState = pagerState)
                }
            }
        )
    }
}