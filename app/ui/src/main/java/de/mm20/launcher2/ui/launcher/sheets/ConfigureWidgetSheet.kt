package de.mm20.launcher2.ui.launcher.sheets

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.calendar.UserCalendar
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.launcher.widgets.external.ExternalWidget
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import kotlinx.coroutines.launch
import org.koin.androidx.compose.get
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ConfigureWidgetSheet(
    appWidgetHost: AppWidgetHost,
    widget: Widget,
    onWidgetUpdated: (Widget) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialog(onDismissRequest = onDismiss,
        title = {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (widget is AppWidget) 8.dp else 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            when (widget) {
                is WeatherWidget -> ConfigureWeatherWidget(widget, onWidgetUpdated)
                is AppWidget -> ConfigureAppWidget(appWidgetHost, widget, onWidgetUpdated)
                is CalendarWidget -> ConfigureCalendarWidget(widget, onWidgetUpdated)
                is FavoritesWidget -> ConfigureFavoritesWidget(widget, onWidgetUpdated)
                is MusicWidget -> ConfigureMusicWidget()
                is NotesWidget -> ConfigureNotesWidget(widget, onWidgetUpdated)
            }
        }

    }
}

@Composable
fun ColumnScope.ConfigureWeatherWidget(
    widget: WeatherWidget,
    onWidgetUpdated: (WeatherWidget) -> Unit,
) {
    val context = LocalContext.current

    OutlinedCard {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.widget_config_weather_compact),
                iconPadding = false,
                value = !widget.config.showForecast,
                onValueChanged = {
                    onWidgetUpdated(widget.copy(config = widget.config.copy(showForecast = !it)))
                }
            )
        }
    }
    TextButton(
        modifier = Modifier
            .padding(top = 8.dp)
            .align(Alignment.End),
        contentPadding = PaddingValues(
            end = 16.dp,
            top = 8.dp,
            start = 24.dp,
            bottom = 8.dp,
        ),
        onClick = {
            context.startActivity(Intent(
                context,
                SettingsActivity::class.java
            ).apply {
                putExtra(
                    SettingsActivity.EXTRA_ROUTE,
                    SettingsActivity.ROUTE_WEATHER_INTEGRATION
                )
            })
        }) {
        Text(stringResource(R.string.widget_config_weather_integration_settings))
        Icon(
            modifier = Modifier
                .padding(start = ButtonDefaults.IconSpacing)
                .requiredSize(ButtonDefaults.IconSize),
            imageVector = Icons.Rounded.OpenInNew, contentDescription = null
        )
    }
}

@Composable
fun ColumnScope.ConfigureFavoritesWidget(
    widget: FavoritesWidget,
    onWidgetUpdated: (FavoritesWidget) -> Unit,
) {
    val bottomSheetManager = LocalBottomSheetManager.current
    OutlinedCard {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.preference_edit_button),
                iconPadding = false,
                value = widget.config.editButton,
                onValueChanged = {
                    onWidgetUpdated(widget.copy(config = widget.config.copy(editButton = it)))
                }
            )
        }
    }
    TextButton(
        modifier = Modifier
            .padding(top = 8.dp)
            .align(Alignment.End),
        contentPadding = PaddingValues(
            end = 16.dp,
            top = 8.dp,
            start = 24.dp,
            bottom = 8.dp,
        ),
        onClick = {
            bottomSheetManager.showEditFavoritesSheet()
        }) {
        Text(stringResource(R.string.menu_item_edit_favs))
        Icon(
            modifier = Modifier
                .padding(start = ButtonDefaults.IconSpacing)
                .requiredSize(ButtonDefaults.IconSize),
            imageVector = Icons.Rounded.OpenInNew, contentDescription = null
        )
    }
}

@Composable
fun ColumnScope.ConfigureMusicWidget(

) {
    val context = LocalContext.current

    TextButton(
        modifier = Modifier
            .align(Alignment.CenterHorizontally),
        contentPadding = PaddingValues(
            end = 16.dp,
            top = 8.dp,
            start = 24.dp,
            bottom = 8.dp,
        ),
        onClick = {
            context.startActivity(Intent(
                context,
                SettingsActivity::class.java
            ).apply {
                putExtra(
                    SettingsActivity.EXTRA_ROUTE,
                    SettingsActivity.ROUTE_MEDIA_INTEGRATION,
                )
            })
        }) {
        Text(stringResource(R.string.widget_config_music_integration_settings))
        Icon(
            modifier = Modifier
                .padding(start = ButtonDefaults.IconSpacing)
                .requiredSize(ButtonDefaults.IconSize),
            imageVector = Icons.Rounded.OpenInNew, contentDescription = null
        )
    }
}

@Composable
fun ColumnScope.ConfigureAppWidget(
    appWidgetHost: AppWidgetHost,
    widget: AppWidget,
    onWidgetUpdated: (Widget) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val widgetInfo = remember(widget.config.widgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.config.widgetId)
    }

    if (widgetInfo == null) {
        var replaceWidget by rememberSaveable {
            mutableStateOf(false)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            LargeMessage(
                icon = Icons.Rounded.Error,
                text = stringResource(id = R.string.app_widget_loading_failed)
            )
        }
        OutlinedButton(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            onClick = { replaceWidget = true }) {
            Icon(
                Icons.Rounded.Build,
                null,
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .requiredSize(ButtonDefaults.IconSize)
            )
            Text(stringResource(R.string.widget_action_replace))
        }
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
                    onWidgetUpdated(updatedWidget)
                    replaceWidget = false
                }
            )
        }
        return
    }

    var dragDelta by remember { mutableStateOf(0) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ExternalWidget(
                appWidgetHost = appWidgetHost,
                widgetInfo = widgetInfo,
                widgetId = widget.config.widgetId,
                modifier = Modifier.fillMaxWidth(),
                height = widget.config.height + dragDelta,
                borderless = widget.config.borderless,
            )
        }
        val density = LocalDensity.current
        val draggableState = rememberDraggableState {
            dragDelta = (dragDelta + it / density.density).roundToInt()
                .coerceIn(
                    -widget.config.height + 1,
                    500 - widget.config.height
                )
        }
        Row(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 16.dp)
                .padding(horizontal = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val scope = rememberCoroutineScope()
            val tooltipState = rememberTooltipState()
            TooltipBox(
                state = tooltipState,
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.widget_config_appwidget_resize_hint))
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .height(36.dp)
                        .width(48.dp)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                onWidgetUpdated(
                                    widget.copy(
                                        config = widget.config.copy(
                                            height = widget.config.height + dragDelta
                                        )
                                    )
                                )
                                dragDelta = 0
                            }
                        )
                        .clickable {
                            scope.launch {
                                tooltipState.show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                    )
                }

            }
            var textFieldValue by remember(widget.config.height) { mutableStateOf(widget.config.height.toString()) }
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 8.dp),
                value = (widget.config.height + dragDelta).toString(),
                onValueChange = {
                    val intValue = it.toIntOrNull()
                    if (intValue == null) textFieldValue = ""
                    else if (intValue in 1..500) {
                        onWidgetUpdated(
                            widget.copy(
                                config = widget.config.copy(
                                    height = intValue
                                )
                            )
                        )
                        textFieldValue = intValue.toString()
                    }
                },
                label = { Text(stringResource(R.string.widget_config_appwidget_height)) },
                suffix = { Text("dp") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
            )
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        OutlinedCard {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.widget_config_appwidget_borderless),
                    iconPadding = false,
                    value = widget.config.borderless,
                    onValueChanged = {
                        onWidgetUpdated(widget.copy(config = widget.config.copy(borderless = it)))
                    }
                )
                Divider()
                SwitchPreference(
                    title = stringResource(R.string.widget_config_appwidget_background),
                    iconPadding = false,
                    value = widget.config.background,
                    onValueChanged = {
                        onWidgetUpdated(widget.copy(config = widget.config.copy(background = it)))
                    }
                )
            }
        }
        if (isAtLeastApiLevel(28) && widgetInfo.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0) {
            TextButton(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End),
                contentPadding = PaddingValues(
                    end = 16.dp,
                    top = 8.dp,
                    start = 24.dp,
                    bottom = 8.dp,
                ),
                onClick = {
                    appWidgetHost.startAppWidgetConfigureActivityForResult(
                        lifecycleOwner as Activity,
                        widget.config.widgetId,
                        0,
                        0,
                        if (Build.VERSION.SDK_INT < 34) {
                            null
                        } else {
                            ActivityOptions.makeBasic()
                                .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                                .setPendingIntentCreatorBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                )
                                .toBundle()
                        }
                    )
                }) {
                Text(
                    stringResource(id = R.string.widget_config_appwidget_configure)
                )
                Icon(
                    modifier = Modifier
                        .padding(start = ButtonDefaults.IconSpacing)
                        .requiredSize(ButtonDefaults.IconSize),
                    imageVector = Icons.Rounded.OpenInNew, contentDescription = null
                )
            }
        }
    }
}

@Composable
fun ColumnScope.ConfigureCalendarWidget(
    widget: CalendarWidget,
    onWidgetUpdated: (CalendarWidget) -> Unit
) {
    val calendarRepository: CalendarRepository = get()
    val permissionsManager: PermissionsManager = get()
    var calendars by remember { mutableStateOf(emptyList<UserCalendar>()) }
    var ready by remember { mutableStateOf(false) }

    val hasPermission by remember {
        permissionsManager.hasPermission(PermissionGroup.Calendar)
    }.collectAsState(true)

    LaunchedEffect(hasPermission) {
        calendars = calendarRepository.getCalendars().sortedBy { it.name }
        ready = true
    }

    OutlinedCard {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.preference_calendar_hide_allday),
                iconPadding = false,
                value = !widget.config.allDayEvents,
                onValueChanged = {
                    onWidgetUpdated(widget.copy(config = widget.config.copy(allDayEvents = !it)))
                }
            )
        }
    }
    Text(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        text = stringResource(R.string.preference_calendar_calendars)
    )
    val context = LocalLifecycleOwner.current as AppCompatActivity
    if (calendars.isNotEmpty()) {
        OutlinedCard {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                for ((i, calendar) in calendars.withIndex()) {
                    if (i > 0) Divider()
                    CheckboxPreference(
                        title = calendar.name,
                        summary = calendar.owner,
                        iconPadding = false,
                        value = !widget.config.excludedCalendarIds.contains(calendar.id),
                        onValueChanged = {
                            onWidgetUpdated(
                                widget.copy(
                                    config = widget.config.copy(
                                        excludedCalendarIds = if (it) {
                                            widget.config.excludedCalendarIds - calendar.id
                                        } else {
                                            widget.config.excludedCalendarIds + calendar.id
                                        }
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }
    } else if (!hasPermission) {
        MissingPermissionBanner(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.missing_permission_calendar_widget_settings),
            onClick = { permissionsManager.requestPermission(context, PermissionGroup.Calendar) },
        )
    } else if (ready) {
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            text = stringResource(R.string.widget_config_calendar_no_calendars)
        )
    }
    if (hasPermission) {
        val colorScheme = MaterialTheme.colorScheme
        TextButton(
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            onClick = {
                CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(colorScheme.primaryContainer.toArgb())
                            .setSecondaryToolbarColor(colorScheme.secondaryContainer.toArgb())
                            .build()
                    )
                    .build().launchUrl(
                        context,
                        Uri.parse("https://kvaesitso.mm20.de/docs/user-guide/widgets/calendar-widget#my-calendars-dont-show-up")
                    )
            }) {
            Icon(
                modifier = Modifier
                    .padding(end = ButtonDefaults.IconSpacing)
                    .requiredSize(ButtonDefaults.IconSize),
                imageVector = Icons.Rounded.HelpOutline, contentDescription = null
            )
            Text(stringResource(R.string.widget_config_calendar_missing_calendars_hint))
        }
    }
}

@Composable
fun ConfigureNotesWidget(
    widget: NotesWidget,
    onWidgetUpdated: (NotesWidget) -> Unit
) {
    val context = LocalContext.current
    val linkFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) {
        it ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            if (widget.config.linkedFile != null) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(widget.config.linkedFile),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    CrashReporter.logException(e)
                }
            }
            onWidgetUpdated(
                widget.copy(
                    config = widget.config.copy(
                        linkedFile = it.toString(),
                        lastSyncSuccessful = false
                    )
                )
            )
        } catch (e: SecurityException) {
            CrashReporter.logException(e)
        }
    }
    OutlinedCard {
        if (widget.config.linkedFile != null) {
            Preference(
                icon = { Icon(Icons.Rounded.LinkOff, null) },
                title = { Text(stringResource(R.string.note_widget_action_unlink_file)) },
                summary = {
                    Text(
                        stringResource(
                            R.string.note_widget_linked_file_summary,
                            formatLinkedFileUri(widget.config.linkedFile?.toUri())
                        )
                    )
                },
                onClick = {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(widget.config.linkedFile),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: SecurityException) {
                        CrashReporter.logException(e)
                    }
                    onWidgetUpdated(widget.copy(config = widget.config.copy(linkedFile = null)))
                }
            )
        } else {
            Preference(
                title = stringResource(R.string.note_widget_link_file),
                summary = stringResource(R.string.note_widget_link_file_summary),
                icon = Icons.Rounded.Link,
                onClick = {
                    linkFileLauncher.launch(
                        context.getString(
                            R.string.notes_widget_export_filename,
                            ZonedDateTime.now().format(
                                DateTimeFormatter.ISO_INSTANT
                            )
                        )
                    )
                }
            )
        }
    }
}

fun formatLinkedFileUri(uri: Uri?): String {
    if (uri == null) return ""
    if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
        return uri.lastPathSegment ?: ""
    }
    return uri.toString()
}