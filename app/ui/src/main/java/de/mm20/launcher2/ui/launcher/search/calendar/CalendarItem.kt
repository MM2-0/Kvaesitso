package de.mm20.launcher2.ui.launcher.search.calendar

import android.content.Context
import android.text.Html
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import palettes.TonalPalette

@Composable
fun CalendarItem(
    modifier: Modifier = Modifier,
    calendar: CalendarEvent,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${calendar.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()
    val timeFormat = LocalTimeFormat.current

    LaunchedEffect(calendar) {
        viewModel.init(calendar, iconSize.toInt())
    }

    val darkMode = LocalDarkTheme.current
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val eventColor = Color(
        TonalPalette
            .fromInt(calendar.color ?: secondaryColor.toArgb())
            .tone(
                if (darkMode) 80 else 40
            )
    )

    SharedTransitionLayout {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(
                                when (calendar.isCompleted) {
                                    true -> R.drawable.check_circle_24px_filled
                                    false -> R.drawable.radio_button_unchecked_24px
                                    null -> R.drawable.circle_24px_filled
                                }
                            ),
                            null,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 20.dp)
                                .size(24.dp)
                                .sharedElement(
                                    rememberSharedContentState("color"),
                                    this@AnimatedContent
                                ),
                            tint = eventColor
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 16.dp)
                        ) {
                            Text(
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("label"),
                                        this@AnimatedContent
                                    ),
                                text = calendar.labelOverride ?: calendar.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                textDecoration = if (calendar.isCompleted == true) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                            if (calendar.calendarName != null) {
                                Text(
                                    modifier = Modifier.animateEnterExit(
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ),
                                    text = calendar.calendarName!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = eventColor,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            painter = painterResource(R.drawable.schedule_24px),
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier
                                .sharedBounds(
                                    rememberSharedContentState("date"),
                                    this@AnimatedContent
                                ),
                            text = calendar.formatTime(context, timeFormat),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!calendar.description.isNullOrBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.notes_24px),
                                contentDescription = null
                            )
                            Text(
                                text = Html.fromHtml(
                                    calendar.description!!,
                                    Html.FROM_HTML_MODE_COMPACT
                                ).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (calendar.attendees.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.group_24px),
                                contentDescription = null
                            )
                            Text(
                                text = calendar.attendees.joinToString(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!calendar.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp)
                                .clickable {
                                    calendar.openLocation(context)
                                }
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.location_on_24px),
                                contentDescription = null
                            )
                            Text(
                                text = calendar.location!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_calendar_open_externally),
                            icon = R.drawable.open_in_new_24px,
                            action = {
                                viewModel.launch(context)
                                onBack()
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(
                        DefaultToolbarAction(
                        label = stringResource(R.string.menu_customize),
                        icon = R.drawable.tune_24px,
                        action = { sheetManager.showCustomizeSearchableModal(calendar) }
                    ))

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                        .padding(16.dp)
                ) {
                    Icon(
                        painterResource(
                            when (calendar.isCompleted) {
                                true -> R.drawable.check_circle_24px_filled
                                false -> R.drawable.radio_button_unchecked_24px
                                null -> R.drawable.circle_24px_filled
                            }
                        ),
                        null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp)
                            .sharedElement(
                                rememberSharedContentState("color"),
                                this@AnimatedContent
                            ),
                        tint = eventColor
                    )
                    Column {
                        Text(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent
                            ),
                            text = calendar.labelOverride ?: calendar.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            textDecoration = if (calendar.isCompleted == true) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
                        )
                        Text(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .sharedBounds(
                                    rememberSharedContentState("date"),
                                    this@AnimatedContent
                                ),
                            text = calendar.getSummary(context, timeFormat),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                }
            }
        }
    }
}

@Composable
fun CalendarItemGridPopup(
    calendar: CalendarEvent,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        CalendarItem(
            modifier = Modifier
                .fillMaxWidth(),
            calendar = calendar,
            showDetails = true,
            onBack = onDismiss
        )
    }
}

private fun CalendarEvent.formatTime(
    context: Context,
    timeFormat: TimeFormat,
): String {
    val timeFormatFlag = when (timeFormat) {
        TimeFormat.System -> 0
        TimeFormat.TwelveHour -> DateUtils.FORMAT_12HOUR
        TimeFormat.TwentyFourHour -> DateUtils.FORMAT_24HOUR
    }

    val startTime = startTime
    if (startTime == null || isTask) {
        if (allDay) {
            return DateUtils.formatDateRange(
                context,
                endTime,
                endTime,
                DateUtils.FORMAT_SHOW_DATE or timeFormatFlag
            )
        }
        return DateUtils.formatDateRange(
            context,
            endTime,
            endTime,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or timeFormatFlag
        )
    }

    if (allDay) return DateUtils.formatDateRange(
        context,
        startTime,
        endTime,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or timeFormatFlag
    )
    return DateUtils.formatDateRange(
        context,
        startTime,
        endTime,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or timeFormatFlag
    )

}

private fun CalendarEvent.getSummary(
    context: Context,
    timeFormat: TimeFormat,
): String {
    val timeFormatFlag = when (timeFormat) {
        TimeFormat.System -> 0
        TimeFormat.TwelveHour -> DateUtils.FORMAT_12HOUR
        TimeFormat.TwentyFourHour -> DateUtils.FORMAT_24HOUR
    }

    val startTime = startTime
    if (isTask || startTime == null) {
        val isToday = DateUtils.isToday(endTime)
        if (isToday) {
            if (allDay) {
                return context.getString(R.string.task_due_today)
            }
            return context.getString(
                R.string.task_due_time, DateUtils.formatDateTime(
                    context,
                    endTime,
                    DateUtils.FORMAT_SHOW_TIME or timeFormatFlag
                )
            )
        }
        if (allDay) {
            return context.getString(
                R.string.task_due_date, DateUtils.formatDateTime(
                    context,
                    endTime,
                    DateUtils.FORMAT_SHOW_DATE or timeFormatFlag
                )
            )
        }
        return context.getString(
            R.string.task_due_date, DateUtils.formatDateTime(
                context,
                endTime,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or timeFormatFlag
            )
        )
    }

    val isToday =
        DateUtils.isToday(startTime) && DateUtils.isToday(endTime)
    return if (isToday) {
        if (allDay) {
            context.getString(R.string.calendar_event_allday)
        } else {
            DateUtils.formatDateRange(
                context,
                startTime,
                endTime,
                DateUtils.FORMAT_SHOW_TIME or timeFormatFlag
            )
        }
    } else {
        if (allDay) {
            DateUtils.formatDateRange(
                context,
                startTime,
                endTime,
                DateUtils.FORMAT_SHOW_DATE or timeFormatFlag
            )
        } else {
            DateUtils.formatDateRange(
                context,
                startTime,
                endTime,
                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or timeFormatFlag
            )
        }
    }
}