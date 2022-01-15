package de.mm20.launcher2.ui.settings.calendarwidget

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.data.UserCalendar
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.pluralResource

@Composable
fun CalendarWidgetSettingsScreen() {
    val viewModel: CalendarWidgetSettingsScreenVM = viewModel()
    val context = LocalContext.current
    PreferenceScreen(title = stringResource(R.string.preference_screen_calendarwidget)) {
        item {
            val excludeAllDayEvents by viewModel.excludeAllDayEvents.observeAsState()
            PreferenceCategory {
                val hasPermission by viewModel.hasCalendarPermission.observeAsState()
                AnimatedVisibility(hasPermission == false) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(R.string.missing_permission_calendar_widget_settings),
                        onClick = {
                            viewModel.requestPermission(context as AppCompatActivity)
                        }
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.preference_calendar_hide_allday),
                    value = excludeAllDayEvents == true,
                    onValueChanged = {
                        viewModel.setExcludeAllDayEvents(it)
                    }
                )
                val calendars by viewModel.calendars.observeAsState(emptyList())
                val unselectedCalendars by viewModel.unselectedCalendars.observeAsState(emptyList())
                ExcludedCalendarsPreference(
                    calendars = calendars,
                    value = unselectedCalendars,
                    onValueChanged = {
                        viewModel.setUnselectedCalendars(it)
                    }
                )
            }
        }
    }
}

@Composable
fun ExcludedCalendarsPreference(
    calendars: List<UserCalendar>,
    value: List<Long>,
    onValueChanged: (List<Long>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        title = stringResource(R.string.preference_calendar_calendars),
        summary = pluralResource(
            R.plurals.preference_calendar_calendars_summary,
            quantity = calendars.size - value.size,
            calendars.size - value.size
        ),
        onClick = {
            showDialog = true
        }
    )
    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 16.dp,
                shadowElevation = 16.dp,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.preference_calendar_calendars),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp
                            )
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        items(calendars) { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (value.contains(c.id)) {
                                            onValueChanged(
                                                value.filter { it != c.id }
                                            )
                                        } else {
                                            onValueChanged(
                                                value + c.id
                                            )
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = !value.contains(c.id),
                                    onCheckedChange = {
                                        if (it) {
                                            onValueChanged(
                                                value.filter { it != c.id }
                                            )
                                        } else {
                                            onValueChanged(
                                                value + c.id
                                            )
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(c.color)
                                    )
                                )
                                Text(text = c.name)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            onValueChanged(value.toList())
                            showDialog = false
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(android.R.string.ok),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}