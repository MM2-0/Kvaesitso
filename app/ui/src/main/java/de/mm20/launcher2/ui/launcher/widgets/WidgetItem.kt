package de.mm20.launcher2.ui.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.Banner
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.sheets.ConfigureWidgetSheet
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.launcher.widgets.calendar.CalendarWidget
import de.mm20.launcher2.ui.launcher.widgets.external.ExternalWidget
import de.mm20.launcher2.ui.launcher.widgets.favorites.FavoritesWidget
import de.mm20.launcher2.ui.launcher.widgets.music.MusicWidget
import de.mm20.launcher2.ui.launcher.widgets.notes.NotesWidget
import de.mm20.launcher2.ui.launcher.widgets.weather.WeatherWidget
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import java.util.UUID

@Composable
fun WidgetItem(
    widget: Widget,
    appWidgetHost: AppWidgetHost,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onWidgetAdd: (widget: Widget) -> Unit = {},
    onWidgetUpdate: (widget: Widget) -> Unit = {},
    onWidgetRemove: () -> Unit = {},
    draggableState: DraggableState = rememberDraggableState {},
    onDragStopped: () -> Unit = {}
) {
    val context = LocalContext.current

    var configure by rememberSaveable { mutableStateOf(false) }

    var isDragged by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDragged) 8.dp else 2.dp)

    val appWidget = if (widget is AppWidget) remember(widget.config.widgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.config.widgetId)
    } else null

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
                        text = when (widget) {
                            is WeatherWidget -> stringResource(R.string.widget_name_weather)
                            is MusicWidget -> stringResource(R.string.widget_name_music)
                            is CalendarWidget -> stringResource(R.string.widget_name_calendar)
                            is FavoritesWidget -> stringResource(R.string.widget_name_favorites)
                            is NotesWidget -> stringResource(R.string.widget_name_notes)
                            is AppWidget -> remember(widget.config.widgetId) {
                                appWidget?.loadLabel(
                                    context.packageManager
                                )
                            }
                                ?: stringResource(R.string.widget_name_unknown)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                    IconButton(onClick = {
                        configure = true
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(onClick = { onWidgetRemove() }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.widget_action_remove)
                        )
                    }
                }
            }
            AnimatedVisibility(!editMode) {
                when (widget) {
                    is WeatherWidget -> {
                        WeatherWidget(widget)
                    }

                    is MusicWidget -> {
                        MusicWidget(widget)
                    }

                    is CalendarWidget -> {
                        CalendarWidget(widget)
                    }

                    is FavoritesWidget -> {
                        FavoritesWidget(widget)
                    }

                    is NotesWidget -> {
                        NotesWidget(
                            widget,
                            onNewNote = {
                                val newWidget = NotesWidget(
                                    id = UUID.randomUUID(),
                                    widget.config.copy(storedText = "")
                                )
                                onWidgetAdd(newWidget)
                            }
                        )
                    }

                    is AppWidget -> {
                        val widgetInfo = remember(widget.config.widgetId) {
                            AppWidgetManager.getInstance(context)
                                .getAppWidgetInfo(widget.config.widgetId)
                        }
                        if (widgetInfo == null) {
                            var replaceWidget by rememberSaveable {
                                mutableStateOf(false)
                            }
                            Banner(
                                modifier = Modifier.padding(16.dp),
                                text = stringResource(R.string.app_widget_loading_failed),
                                icon = Icons.Rounded.Warning,
                                secondaryAction = {
                                    OutlinedButton(onClick = onWidgetRemove) {
                                        Text(stringResource(R.string.widget_action_remove))
                                    }
                                },
                                primaryAction = {
                                    Button(onClick = { replaceWidget = true }) {
                                        Text(stringResource(R.string.widget_action_replace))
                                    }
                                }
                            )
                            if (replaceWidget) {
                                WidgetPickerSheet(
                                    onDismiss = { replaceWidget = false },
                                    onWidgetSelected = {
                                        val updatedWidget = when (it) {
                                            is AppWidget -> widget.copy(
                                                config = widget.config.copy(
                                                    widgetId = it.config.widgetId
                                                )
                                            )
                                            is WeatherWidget -> it.copy(id = widget.id)
                                            is MusicWidget -> it.copy(id = widget.id)
                                            is CalendarWidget -> it.copy(id = widget.id)
                                            is FavoritesWidget -> it.copy(id = widget.id)
                                            is NotesWidget -> it.copy(id = widget.id)
                                        }
                                        onWidgetUpdate(updatedWidget)
                                        replaceWidget = false
                                    }
                                )
                            }
                        } else {
                            ExternalWidget(
                                appWidgetHost = appWidgetHost,
                                widgetId = widget.config.widgetId,
                                widgetInfo = widgetInfo,
                                modifier = Modifier.fillMaxWidth(),
                                height = widget.config.height,
                                borderless = widget.config.borderless,
                            )
                        }
                    }
                }
            }
        }
    }
    if (configure) {
        ConfigureWidgetSheet(
            appWidgetHost = appWidgetHost,
            widget = widget,
            onWidgetUpdated = onWidgetUpdate,
            onDismiss = { configure = false },
        )
    }
}
