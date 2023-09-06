package de.mm20.launcher2.ui.launcher.search.calendar

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
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
import de.mm20.launcher2.ui.locals.LocalSnackbarHostState
import kotlinx.coroutines.launch
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

    LaunchedEffect(calendar) {
        viewModel.init(calendar, iconSize.toInt())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = LocalSnackbarHostState.current

    val darkMode = LocalDarkTheme.current

    Row(
        modifier = modifier
            .drawBehind {
                val color = TonalPalette
                    .fromInt(calendar.color)
                    .tone(
                        if (darkMode) 80 else 40
                    )
                drawRect(Color(color), Offset.Zero, this.size.copy(width = 8.dp.toPx()))
            }
            .padding(start = 8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                val padding by animateDpAsState(if (showDetails) 16.dp else 12.dp)
                Column(
                    modifier = Modifier.padding(
                        top = padding,
                        start = padding,
                        bottom = 12.dp,
                        end = padding
                    )
                ) {
                    val textStyle by animateTextStyleAsState(
                        if (showDetails) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall
                    )
                    Text(text = calendar.labelOverride ?: calendar.label, style = textStyle)
                    AnimatedVisibility(!showDetails) {
                        Text(
                            modifier = Modifier.padding(top = 2.dp),
                            text = calendar.getSummary(context),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    AnimatedVisibility(showDetails) {
                        val tags by viewModel.tags.collectAsState(emptyList())
                        if (tags.isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
                                text = tags.joinToString(separator = " #", prefix = "#"),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(showDetails) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null
                        )
                        Text(
                            text = calendar.formatTime(context),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (calendar.description.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.Notes,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (calendar.attendees.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.People,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.attendees.joinToString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (calendar.location.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    calendar.openLocation(context)
                                }
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                imageVector = Icons.Rounded.Place,
                                contentDescription = null
                            )
                            Text(
                                text = calendar.location,
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
                                icon = Icons.Rounded.Star,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = Icons.Rounded.StarOutline,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    val isHidden by viewModel.isHidden.collectAsState(false)
                    val hideAction = if (isHidden) {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_unhide),
                            icon = Icons.Rounded.Visibility,
                            action = {
                                viewModel.unhide()
                                onBack()
                            }
                        )
                    } else {
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_hide),
                            icon = Icons.Rounded.VisibilityOff,
                            action = {
                                viewModel.hide()
                                onBack()
                                lifecycleOwner.lifecycleScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.msg_item_hidden,
                                            calendar.label
                                        ),
                                        actionLabel = context.getString(R.string.action_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.unhide()
                                    }
                                }
                            })
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_calendar_open_externally),
                            icon = Icons.Rounded.OpenInNew,
                            action = {
                                viewModel.launch(context)
                                onBack()
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(DefaultToolbarAction(
                        label = stringResource(R.string.menu_customize),
                        icon = Icons.Rounded.Edit,
                        action = { sheetManager.showCustomizeSearchableModal(calendar) }
                    ))

                    toolbarActions.add(hideAction)

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = Icons.Rounded.ArrowBack
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
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
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.roundToIntRect().size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.roundToIntRect().size },
    ) {
        CalendarItem(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(calendar.color).copy(alpha = 1f - animationProgress)),
            calendar = calendar,
            showDetails = true,
            onBack = onDismiss
        )
    }
}

private fun CalendarEvent.formatTime(context: Context): String {
    if (allDay) return DateUtils.formatDateRange(
        context,
        startTime,
        endTime,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
    )
    return DateUtils.formatDateRange(
        context,
        startTime,
        endTime,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY
    )

}

private fun CalendarEvent.getSummary(context: Context): String {
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
                DateUtils.FORMAT_SHOW_TIME
            )
        }
    } else {
        if (allDay) {
            DateUtils.formatDateRange(
                context,
                startTime,
                endTime,
                DateUtils.FORMAT_SHOW_DATE
            )
        } else {
            DateUtils.formatDateRange(
                context,
                startTime,
                endTime,
                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
            )
        }
    }
}