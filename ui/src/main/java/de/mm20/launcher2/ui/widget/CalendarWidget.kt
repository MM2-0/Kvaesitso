package de.mm20.launcher2.ui.widget

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.calendar.CalendarViewModel
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.InformationText
import de.mm20.launcher2.ui.LauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.pluralResource
import de.mm20.launcher2.ui.searchable.DeprecatedSearchableList
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Composable
fun CalendarWidget() {

    val viewModel: CalendarViewModel = viewModel()
    val favViewModel: FavoritesViewModel = viewModel()

    val events by viewModel.upcomingCalendarEvents.observeAsState()
    val pinnedEvents by favViewModel.pinnedCalendarEvents.observeAsState(emptyList())
    val today = getToday()
    val availableDays: List<Long> = remember(events) {
        events?.map { ((it.startTime + zoneOffset) / (1000 * 60 * 60 * 24)) - today }
            ?.union(events?.map { ((it.endTime + zoneOffset) / (1000 * 60 * 60 * 24)) - today }
                ?: emptyList())
            ?.union(listOf(0L))
            ?.toSet()?.toList()?.sorted() ?: emptyList()
    }

    var selectedDay by remember { mutableStateOf(0L) }

    var showAll by remember { mutableStateOf(false) }

    val pastEvents =
        events?.filter { (it.startTime + zoneOffset) / (1000 * 60 * 60 * 24) < today + selectedDay && (it.endTime + zoneOffset) / (1000 * 60 * 60 * 24) > today + selectedDay }

    var noEvents = true
    val selectedEvents = remember(today, events, selectedDay, showAll) {
        events
            ?.filter { (it.startTime + zoneOffset) / (1000 * 60 * 60 * 24) == today + selectedDay || (it.endTime + zoneOffset) / (1000 * 60 * 60 * 24) == today + selectedDay }
            ?.also { noEvents = it.isEmpty() }
            ?.union(if (showAll && pastEvents != null) pastEvents else emptyList())?.toList()
            ?: listOf<Searchable>()
    }



    Column(
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            DaySelector(
                availableDays = availableDays,
                modifier = Modifier.weight(1f),
                onSelectDay = {
                    selectedDay = it
                    showAll = false
                }
            )
            IconButton(onClick = { /*TODO*/ }) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.calendar_menu_open_externally),
                )
            }
            IconButton(onClick = { /*TODO*/ }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.calendar_widget_new_event)
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (noEvents) {
                InformationText(
                    text = stringResource(id = R.string.calendar_widget_no_events)
                )
            }
            DeprecatedSearchableList(
                items = selectedEvents,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (!showAll && pastEvents?.isNotEmpty() == true) {
                InformationText(
                    text = pluralResource(
                        R.plurals.calendar_widget_running_events,
                        pastEvents.size,
                        pastEvents.size
                    ),
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = {
                        showAll = true
                    }
                )
            }
            if (pinnedEvents.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.calendar_widget_pinned_events),
                    style = MaterialTheme.typography.h1
                )
                DeprecatedSearchableList(
                    items = pinnedEvents,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }


    }
}

@Composable
fun DaySelector(
    modifier: Modifier = Modifier,
    availableDays: List<Long>,
    onSelectDay: (Long) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf(0L) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            val i = max(1, availableDays.indexOf(selectedDay))
            selectedDay = availableDays[i - 1]
            onSelectDay(selectedDay)
        }) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null
            )
        }
        Row(
            modifier = Modifier
                .weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = {
                        menuExpanded = true
                    })
                    .padding(all = 12.dp)
                    .wrapContentWidth()
                    .animateContentSize()
            ) {
                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier
                        .wrapContentWidth(),
                    text = formatDay(LocalContext.current, selectedDay),
                    style = MaterialTheme.typography.h1
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    modifier = Modifier.size(24.dp),
                    contentDescription = null
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = {
                menuExpanded = false
            }) {

                for (day in availableDays) {
                    DropdownMenuItem(onClick = {
                        selectedDay = day
                        menuExpanded = false
                        onSelectDay(selectedDay)
                    }) {
                        Text(text = formatDay(LocalContext.current, day))
                    }
                }
            }
        }


        IconButton(onClick = {
            val i = min(availableDays.lastIndex - 1, availableDays.indexOf(selectedDay))
            selectedDay = availableDays[i + 1]
            onSelectDay(selectedDay)
        }) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null
            )
        }
    }
}

private fun getToday(): Long {
    return (System.currentTimeMillis() + zoneOffset) / (1000 * 60 * 60 * 24)
}

private val zoneOffset
    get() = Calendar.getInstance().timeZone.getOffset(System.currentTimeMillis()).toLong()

private fun formatDay(context: Context, day: Long): String {
    return when (day) {
        0L -> context.getString(R.string.date_today)
        1L -> context.getString(R.string.date_tomorrow)
        else -> DateUtils.formatDateTime(
            context,
            (getToday() + day) * (1000 * 60 * 60 * 24),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        )
    }
}

object CalendarWidgetShim {
    fun getLegacyView(context: Context): View {
        val composeView = ComposeView(context)
        composeView.id = FrameLayout.generateViewId()
        composeView.setContent {
            LauncherTheme {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colors.onSurface) {
                    Column {
                        CalendarWidget()
                    }
                }
            }
        }
        return composeView
    }
}