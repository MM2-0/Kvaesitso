package de.mm20.launcher2.ui.launcher.search.calendar

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.data.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.animation.animateTextStyleAsState
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled

@Composable
fun CalendarItem(
    modifier: Modifier = Modifier,
    calendar: CalendarEvent,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember(calendar.key) { CalendarItemVM(calendar) }

    Row(
        modifier = modifier
            .drawBehind {
                drawRect(Color(calendar.color), Offset.Zero, this.size.copy(width = 8.dp.toPx()))
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
                    Text(text = calendar.label, style = textStyle)
                    AnimatedVisibility(!showDetails) {
                        Text(
                            text = viewModel.getSummary(context),
                            style = MaterialTheme.typography.bodySmall
                        )
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
                            text = viewModel.formatTime(context),
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
                                    viewModel.openLocation(context as AppCompatActivity)
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
                                label = stringResource(R.string.favorites_menu_unpin),
                                icon = Icons.Rounded.Star,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.favorites_menu_pin),
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
                            })
                    }
                    toolbarActions.add(hideAction)

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.calendar_menu_open_externally),
                            icon = Icons.Rounded.OpenInNew,
                            action = {
                                viewModel.launch(context as AppCompatActivity)
                                onBack()
                            }
                        )
                    )
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalendarItemGridPopup(
    calendar: CalendarEvent,
    show: Boolean,
    animationProgress: Float,
    origin: Rect,
    onDismiss: () -> Unit
) {
    AnimatedContent(
        targetState = show,
        transitionSpec = {
            fadeIn(snap()) with
                    fadeOut(snap(400)) using
                    SizeTransform { _, _ ->
                        tween(300)
                    }
        }
    ) { targetState ->
        if (targetState) {
            CalendarItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(calendar.color).copy(alpha = 1f - animationProgress)),
                calendar = calendar,
                showDetails = true,
                onBack = onDismiss
            )
        } else {
            Box(
                modifier = Modifier
                    .requiredWidth(origin.width.toDp())
                    .requiredHeight(origin.height.toDp())
            )
        }
    }
}