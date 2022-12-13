package de.mm20.launcher2.ui.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget
import de.mm20.launcher2.ui.launcher.widgets.external.ExternalWidget
import de.mm20.launcher2.ui.launcher.widgets.favorites.FavoritesWidget
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidget
import de.mm20.launcher2.ui.launcher.widgets.weather.WeatherWidget
import de.mm20.launcher2.widgets.*
import java.lang.Integer.max
import kotlin.math.roundToInt

@Composable
fun WidgetItem(
    widget: Widget,
    appWidgetHost: AppWidgetHost,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetResize: (newHeight: Int) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {}
) {
    val context = LocalContext.current
    var resizeMode by remember(editMode) { mutableStateOf(false) }

    var isDragged by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragged) 8.dp else 2.dp)

    LauncherCard(
        modifier = modifier.zIndex(if (isDragged) 1f else 0f),
        elevation = elevation
    ) {
        Column {
            AnimatedVisibility(editMode) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = null,
                        modifier = Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            startDragImmediately = true,
                            onDragStarted = {
                                isDragged = true
                            },
                            onDragStopped = {
                                isDragged = false
                                onDragStopped()
                            }
                        )
                    )
                    Text(
                        text = remember(widget) { widget.loadLabel(context) },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    if (widget is ExternalWidget) {
                        IconButton(onClick = { resizeMode = !resizeMode }) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.widget_action_adjust_height)
                            )
                        }
                    }
                    if (widget.isConfigurable) {
                        IconButton({
                            widget.configure(context as Activity, appWidgetHost)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                    IconButton(onClick = { onWidgetRemove() }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.widget_action_remove)
                        )
                    }
                }
            }
            AnimatedVisibility(!editMode || resizeMode) {
                when (widget) {
                    is WeatherWidget -> {
                        WeatherWidget()
                    }
                    is MusicWidget -> {
                        MusicWidget()
                    }
                    is CalendarWidget -> {
                        CalendarWidget()
                    }
                    is FavoritesWidget -> {
                        FavoritesWidget()
                    }
                    is ExternalWidget -> {
                        var height by remember(widget) { mutableStateOf(widget.height) }
                        Column {
                            ExternalWidget(
                                appWidgetHost = appWidgetHost,
                                widgetId = widget.widgetId,
                                modifier = Modifier.fillMaxWidth(),
                                height = height,
                            )
                            if (resizeMode) {
                                val density = LocalDensity.current
                                val drgStt = rememberDraggableState {
                                    height += (it / density.density).roundToInt()
                                }
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .requiredHeight(24.dp)
                                        .fillMaxWidth()
                                        .draggable(
                                            state = drgStt,
                                            orientation = Orientation.Vertical,
                                            startDragImmediately = true,
                                            onDragStopped = {
                                                onWidgetResize(height)
                                            }
                                        )
                                )
                            }

                        }
                    }
                }
            }
        }
    }
}