package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.Context
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.InnerCard
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList
import de.mm20.launcher2.ui.pluralResource
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarWidget() {
    val viewModel: CalendarWidgetVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onActive()
        }
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
                val selectedDate by viewModel.selectedDate.observeAsState(LocalDate.now())
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
        val events by viewModel.calendarEvents.observeAsState(emptyList())
        val hasPermission by viewModel.hasPermission.observeAsState()
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .animateContentSize()
        ) {
            if (hasPermission == false) {
                MissingPermissionBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    text = stringResource(R.string.permission_calendar_widget),
                    onClick = { viewModel.requestCalendarPermission(context as AppCompatActivity) }
                )
            }
            if (events.isEmpty() && hasPermission == true) {
                Info(text = stringResource(R.string.calendar_widget_no_events))
            }
            SearchResultList(
                events,
                modifier = Modifier
                    .fillMaxWidth()
            )
            val runningEvents by viewModel.hiddenPastEvents.observeAsState(0)
            if (runningEvents > 0) {
                Info(
                    text = pluralResource(
                        R.plurals.calendar_widget_running_events,
                        runningEvents,
                        runningEvents
                    ),
                    onClick = {
                        viewModel.showAllEvents()
                    }
                )
            }
            val pinnedEvents by viewModel.pinnedCalendarEvents.observeAsState(emptyList())
            if (pinnedEvents.size > 0) {
                Text(
                    stringResource(R.string.calendar_widget_pinned_events),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
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