package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.Context
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.Quadruple
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList
import de.mm20.launcher2.widgets.CalendarWidget
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarWidget(
    widget: CalendarWidget,
) {
    val viewModel: CalendarWidgetVM = viewModel(key = "calendar-widget-${widget.id}")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedDate by viewModel.selectedDate

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onActive()
        }
    }

    LaunchedEffect(widget) {
        viewModel.updateWidget(widget)
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = { viewModel.previousDay() }) {
                Icon(imageVector = Icons.Rounded.ChevronLeft, contentDescription = null)
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                var showDropdown by remember { mutableStateOf(false) }
                TextButton(onClick = { showDropdown = true }) {
                    Text(
                        text = formatDay(context, selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    val availableDates = viewModel.availableDates
                    for (date in availableDates) {
                        DropdownMenuItem(text = {
                            Text(formatDay(context, date))
                        }, onClick = {
                            viewModel.selectDate(date)
                            showDropdown = false
                        })
                    }
                }
            }
            IconButton(onClick = { viewModel.nextDay() }) {
                Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null)
            }
            IconButton(onClick = { viewModel.createEvent(context) }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            }
            IconButton(onClick = { viewModel.openCalendarApp(context) }) {
                Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null)
            }
        }
        val events by viewModel.calendarEvents
        val nextEvents by viewModel.nextEvents
        val runningEvents by viewModel.hiddenPastEvents
        val hasPermission by viewModel.hasPermission.collectAsState()
        Column {
            if (hasPermission == false) {
                MissingPermissionBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .padding(horizontal = 12.dp),
                    text = stringResource(R.string.missing_permission_calendar_widget),
                    onClick = { viewModel.requestCalendarPermission(context as AppCompatActivity) }
                )
            }
            AnimatedContent(
                Quadruple(
                    selectedDate,
                    events,
                    runningEvents,
                    nextEvents
                ),
                transitionSpec = {
                    when {
                        initialState.first == targetState.first -> fadeIn() togetherWith fadeOut()
                        initialState.first < targetState.first -> {
                            fadeIn() + slideIn {
                                IntOffset(
                                    (it.width * 0.25f).toInt(),
                                    0
                                )
                            } togetherWith
                                    fadeOut() + slideOut {
                                IntOffset(
                                    (it.width * -0.25f).toInt(),
                                    0
                                )
                            }
                        }

                        else -> {
                            fadeIn() + slideIn {
                                IntOffset(
                                    (it.width * -0.25f).toInt(),
                                    0
                                )
                            } togetherWith
                                    fadeOut() + slideOut {
                                IntOffset(
                                    (it.width * 0.25f).toInt(),
                                    0
                                )
                            }
                        }
                    }
                }
            ) { (_, events, runningEvents, nextEvents) ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    if (events.isEmpty() && hasPermission == true) {
                        Info(text = stringResource(R.string.calendar_widget_no_events))
                    }
                    SearchResultList(
                        events,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    if (runningEvents > 0) {
                        Info(
                            text = pluralStringResource(
                                R.plurals.calendar_widget_running_events,
                                runningEvents,
                                runningEvents
                            ),
                            onClick = {
                                viewModel.showAllEvents()
                            }
                        )
                    }
                    if (nextEvents.isNotEmpty()) {
                        Text(
                            stringResource(R.string.calendar_widget_next_events),
                            modifier = Modifier.padding(
                                start = 4.dp,
                                end = 4.dp,
                                top = 8.dp,
                                bottom = 4.dp
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        SearchResultList(
                            nextEvents,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }

            val pinnedEvents by viewModel.pinnedCalendarEvents.collectAsState()
            if (pinnedEvents.isNotEmpty()) {
                Text(
                    stringResource(R.string.calendar_widget_pinned_events),
                    modifier = Modifier.padding(
                        start = 4.dp,
                        end = 4.dp,
                        top = 8.dp,
                        bottom = 4.dp
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                SearchResultList(
                    pinnedEvents,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }

}

@Composable
private fun Info(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    InnerCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .padding(12.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}


private fun formatDay(context: Context, day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        today == day -> context.getString(R.string.date_today)
        today.plusDays(1) == day -> context.getString(R.string.date_tomorrow)
        else -> DateUtils.formatDateTime(
            context,
            day.atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        )
    }
}