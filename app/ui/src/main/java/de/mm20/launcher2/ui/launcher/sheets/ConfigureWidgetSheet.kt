package de.mm20.launcher2.ui.launcher.sheets

import android.app.Activity
import android.app.ActivityOptions
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.plugin.PluginRepository
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.search.calendar.CalendarListType
import de.mm20.launcher2.themes.colors.atTone
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.LocalAppWidgetHost
import de.mm20.launcher2.ui.common.TagChip
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.DragResizeHandle
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropRow
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.component.preferences.CheckboxPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidgetHost
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.settings.SettingsActivity
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.CalendarWidget
import de.mm20.launcher2.widgets.FavoritesWidget
import de.mm20.launcher2.widgets.MusicWidget
import de.mm20.launcher2.widgets.NotesWidget
import de.mm20.launcher2.widgets.WeatherWidget
import de.mm20.launcher2.widgets.Widget
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ConfigureWidgetSheet(
    widget: Widget,
    onWidgetUpdated: (Widget) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheetDialog(
        onDismissRequest = onDismiss,
        windowInsets = WindowInsets()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (widget is AppWidget) 8.dp else 16.dp)
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            when (widget) {
                is WeatherWidget -> ConfigureWeatherWidget(widget, onWidgetUpdated)
                is AppWidget -> ConfigureAppWidget(widget, onWidgetUpdated)
                is CalendarWidget -> ConfigureCalendarWidget(widget, onWidgetUpdated)
                is FavoritesWidget -> ConfigureFavoritesWidget(widget, onWidgetUpdated)
                is MusicWidget -> ConfigureMusicWidget(widget, onWidgetUpdated)
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
            painter = painterResource(R.drawable.open_in_new_20px), contentDescription = null
        )
    }
}

@Composable
fun ColumnScope.ConfigureFavoritesWidget(
    widget: FavoritesWidget,
    onWidgetUpdated: (FavoritesWidget) -> Unit,
) {
    val viewModel: ConfigureWidgetSheetVM = viewModel()

    LaunchedEffect(null) {
        viewModel.reload(widget)
    }

    val availableTags by viewModel.availableTags
    val tagsListState = rememberLazyListState()

    val bottomSheetManager = LocalBottomSheetManager.current

    val selectedTags by viewModel.selectedTags

    var createTag by remember { mutableStateOf(false) }

    OutlinedCard {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    checked = widget.config.showFavorites,
                    onCheckedChange = {
                        if(it || widget.config.showTags)
                        {
                            onWidgetUpdated(widget.copy(config = widget.config.copy(showFavorites = it)))
                        }

                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Icon(
                            painter = painterResource(R.drawable.star_20px),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                        Text(text = stringResource(R.string.favorites))
                    }
                }
                SegmentedButton(
                    checked = widget.config.showTags,
                    onCheckedChange = {
                        if(it || widget.config.showFavorites) {
                            var tagList = selectedTags
                            if(!it)
                            {
                                tagList = emptyList()
                                viewModel.clearAllTags();
                            }

                            onWidgetUpdated(
                                widget.copy(
                                    config = widget.config.copy(showTags = it, tagList = tagList)
                                )
                            )
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Icon(
                            painter = painterResource(R.drawable.tag_20px),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                        Text(text = stringResource(R.string.preference_screen_tags))
                    }
                }
            }
            SwitchPreference(
                title = stringResource(R.string.preference_edit_button),
                iconPadding = false,
                value = widget.config.editButton,
                onValueChanged = {
                    onWidgetUpdated(widget.copy(config = widget.config.copy(editButton = it)))
                }
            )
            if(widget.config.showTags) {
                SwitchPreference(
                    title = stringResource(R.string.preference_compact_tags),
                    iconPadding = false,
                    value = widget.config.compactTags,
                    onValueChanged = {
                        onWidgetUpdated(widget.copy(config = widget.config.copy(compactTags = it)))
                    }
                )
            }
            if(widget.config.showTags) {
                var showAddMenu by remember { mutableStateOf(false) }
                Column {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            text = stringResource(R.string.edit_favorites_dialog_tags),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Box() {
                            FilledTonalIconButton(
                                modifier = Modifier.offset(x = 4.dp),
                                onClick = {
                                    showAddMenu = true
                                }) {
                                Icon(
                                    painter = painterResource(R.drawable.add_24px),
                                    contentDescription = null
                                )
                            }
                            DropdownMenuPopup(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }) {
                                if (availableTags.isNotEmpty()) {
                                    DropdownMenuGroup(
                                        shapes = MenuDefaults.groupShape(0, 2),
                                    ) {
                                        for ((i, tag) in availableTags.withIndex()) {
                                            DropdownMenuItem(
                                                shape = if (availableTags.size == 1) MenuDefaults.standaloneItemShape
                                                else when (i) {
                                                    0 -> MenuDefaults.leadingItemShape
                                                    availableTags.lastIndex -> MenuDefaults.trailingItemShape
                                                    else -> MenuDefaults.middleItemShape
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        painterResource(R.drawable.tag_24px),
                                                        null
                                                    )
                                                },
                                                text = { Text(tag.tag) },
                                                onClick = {
                                                    onWidgetUpdated(
                                                        widget.copy(
                                                            config = widget.config.copy(
                                                                tagList = selectedTags + tag.tag
                                                            )
                                                        )
                                                    )
                                                    viewModel.addTag(tag)
                                                    showAddMenu = false
                                                })
                                        }
                                    }
                                }
                                Spacer(
                                    modifier = Modifier.height(MenuDefaults.GroupSpacing)
                                )
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShape(
                                        if (availableTags.isEmpty()) 0 else 1,
                                        if (availableTags.isEmpty()) 1 else 2,
                                    )
                                ) {
                                    DropdownMenuItem(
                                        shape = MenuDefaults.standaloneItemShape,
                                        leadingIcon = {
                                            Icon(painterResource(R.drawable.add_24px), null)
                                        },
                                        text = {
                                            Text(
                                                stringResource(R.string.edit_favorites_dialog_new_tag),
                                            )
                                        },
                                        onClick = {
                                            createTag = true
                                            showAddMenu = false
                                        }
                                    )
                                }
                            }
                        }

                    }
                    if (selectedTags.isNotEmpty()) {
                        val rowState = rememberLazyDragAndDropListState(
                            listState = tagsListState,
                            onDragEnd = {
                                onWidgetUpdated(
                                    widget.copy(
                                        config = widget.config.copy(
                                            tagList = viewModel.selectedTags.value
                                        )
                                    )
                                )
                            },
                            onItemMove = { from, to ->
                                viewModel.moveItem(from, to)
                            }
                        )
                        LazyDragAndDropRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            state = rowState
                        ) {
                            items(
                                selectedTags,
                                key = { it }
                            ) { tag ->
                                DraggableItem(state = rowState, key = tag) { dragged ->
                                    val tagVal = Tag(tag)
                                    TagChip(
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .pointerInput(null) {
                                            },
                                        tag = tagVal,
                                        dragged = dragged,
                                        clearable = true,
                                        onClear = {
                                            onWidgetUpdated(
                                                widget.copy(
                                                    config = widget.config.copy(
                                                        tagList = selectedTags - tag
                                                    )
                                                )
                                            )
                                            viewModel.clearTag(tagVal)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            text = stringResource(R.string.shortcuts_widget_tag_section_empty),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
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
            painter = painterResource(R.drawable.open_in_new_20px), contentDescription = null
        )
    }

    if (createTag) {
        EditTagSheet(
            tag = null,
            onTagSaved = { tag ->
                val newTag = Tag(tag)
                viewModel.addAvailableTag(newTag)
            },
            onDismiss = {
                createTag = false
            }
        )
    }
}

@Composable
fun ColumnScope.ConfigureMusicWidget(
    widget: MusicWidget,
    onWidgetUpdated: (MusicWidget) -> Unit,

) {
    val context = LocalContext.current

    OutlinedCard {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.music_widget_interactive_progress_bar),
                iconPadding = false,
                value = widget.config.interactiveProgressBar,
                onValueChanged = {
                    onWidgetUpdated(widget.copy(config = widget.config.copy(interactiveProgressBar = it)))
                }
            )
        }
    }

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
            painter = painterResource(R.drawable.open_in_new_20px), contentDescription = null
        )
    }
}

@Composable
fun ColumnScope.ConfigureAppWidget(
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
                icon = R.drawable.error_48px,
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
                painterResource(R.drawable.build_20px),
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

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
        ) {
            var resizeWidth by remember {
                mutableStateOf(widget.config.width?.dp ?: Dp.Unspecified)
            }

            var resizeHeight by remember {
                mutableStateOf(widget.config.height.dp)
            }

            AppWidgetHost(
                widgetInfo = widgetInfo,
                widgetId = widget.config.widgetId,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        if (resizeWidth.isUnspecified) Modifier.fillMaxWidth()
                        else Modifier.width(resizeWidth)
                    )
                    .height(resizeHeight)
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val event = awaitFirstDown(pass = PointerEventPass.Initial)
                            event.consume()
                        }
                    },
                borderless = widget.config.borderless,
                useThemeColors = widget.config.themeColors,
                onLightBackground = (!LocalDarkTheme.current && widget.config.background) || LocalPreferDarkContentOverWallpaper.current
            )

            val maxWidth = if (isAtLeastApiLevel(31)) {
                widgetInfo.maxResizeWidth.takeIf { it > 0 }?.toDp() ?: Dp.Unspecified
            } else Dp.Unspecified

            val maxHeight = if (isAtLeastApiLevel(31)) {
                widgetInfo.maxResizeHeight.takeIf { it > 0 }?.toDp() ?: 2000.dp
            } else 2000.dp

            val minWidth = if (widgetInfo.minResizeWidth in 1..widgetInfo.minWidth) {
                widgetInfo.minResizeWidth.toDp()
            } else {
                widgetInfo.minWidth.toDp()
            }

            val minHeight = if (widgetInfo.minResizeHeight in 1..widgetInfo.minHeight) {
                widgetInfo.minResizeHeight.toDp()
            } else {
                widgetInfo.minHeight.toDp()
            }

            DragResizeHandle(
                alignment = Alignment.TopCenter,
                height = resizeHeight,
                width = resizeWidth,
                minWidth = minWidth,
                minHeight = minHeight,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                snapToMeasuredWidth = true,
                onResize = { w, h ->
                    resizeWidth = w
                    resizeHeight = h
                },
                onResizeStopped = {
                    onWidgetUpdated(
                        widget.copy(
                            config = widget.config.copy(
                                height = resizeHeight.value.roundToInt(),
                                width = resizeWidth.takeIf { it != Dp.Unspecified }?.value?.roundToInt()
                            )
                        )
                    )
                }
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
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.widget_config_appwidget_background),
                    iconPadding = false,
                    value = widget.config.background,
                    onValueChanged = {
                        onWidgetUpdated(widget.copy(config = widget.config.copy(background = it)))
                    }
                )
                if (isAtLeastApiLevel(31)) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(R.string.widget_use_theme_colors),
                        iconPadding = false,
                        value = widget.config.themeColors,
                        onValueChanged = {
                            onWidgetUpdated(widget.copy(config = widget.config.copy(themeColors = it)))
                        }
                    )
                }
            }
        }
        if (isAtLeastApiLevel(28) && widgetInfo.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0) {
            val appWidgetHost = LocalAppWidgetHost.current
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
                    painter = painterResource(R.drawable.open_in_new_20px), contentDescription = null
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
    val calendarRepository: CalendarRepository = koinInject()
    val permissionsManager: PermissionsManager = koinInject()
    val pluginRepository: PluginRepository = koinInject()
    val calendars by remember {
        calendarRepository.getCalendars().map {
            it.sortedBy { it.name }
        }
    }.collectAsState(null)
    val plugins by remember {
        pluginRepository.findMany(
            type = PluginType.Calendar,
            enabled = true,
        )
    }.collectAsState(emptyList())

    val hasPermission by remember {
        permissionsManager.hasPermission(PermissionGroup.Calendar)
    }.collectAsState(true)

    val hasTasks = remember(calendars) {
        calendars?.any { it.types.contains(CalendarListType.Tasks) } == true
    }

    AnimatedVisibility(hasTasks) {
        OutlinedCard {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.preference_calendar_hide_completed),
                    iconPadding = false,
                    value = !widget.config.completedTasks,
                    onValueChanged = {
                        onWidgetUpdated(widget.copy(config = widget.config.copy(completedTasks = !it)))
                    }
                )
            }
        }
    }
    val context = LocalLifecycleOwner.current as AppCompatActivity
    val excludedCalendars = remember(widget.config) {
        widget.config.excludedCalendarIds
            ?: widget.config.legacyExcludedCalendarIds?.map { "local:$it" } ?: emptyList()
    }

    val groups = remember(calendars) {
        calendars?.groupBy { it.providerId }?.entries
    }

    if (groups?.isNotEmpty() == true) {
        for (group in groups) {
            val pluginName = remember(plugins, group.key) {
                if (group.key == "local") context.getString(R.string.preference_calendar_calendars)
                else if (group.key == "tasks.org") context.getString(R.string.preference_search_tasks)
                else plugins.find { it.authority == group.key }?.label
            }
            if (pluginName != null) {
                Text(
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    text = pluginName
                )
            }
            OutlinedCard {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for ((i, calendar) in group.value.withIndex()) {
                        if (i > 0) HorizontalDivider()
                        CheckboxPreference(
                            title = calendar.name,
                            summary = calendar.owner,
                            iconPadding = false,
                            value = !excludedCalendars.contains(calendar.id),
                            onValueChanged = {
                                onWidgetUpdated(
                                    widget.copy(
                                        config = widget.config.copy(
                                            excludedCalendarIds = if (it) {
                                                excludedCalendars - calendar.id
                                            } else {
                                                excludedCalendars + calendar.id
                                            }
                                        )
                                    )
                                )
                            },
                            checkboxColors = CheckboxDefaults.colors(
                                checkedColor = if (calendar.color == 0) MaterialTheme.colorScheme.primary
                                else Color(
                                    calendar.color.atTone(if (LocalDarkTheme.current) 80 else 40)
                                ),
                                checkmarkColor = if (calendar.color == 0) MaterialTheme.colorScheme.onPrimary
                                else Color(
                                    calendar.color.atTone(if (LocalDarkTheme.current) 20 else 100)
                                )
                            )
                        )
                    }
                }
            }
        }
    } else if (!hasPermission) {
        MissingPermissionBanner(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.missing_permission_calendar_widget_settings),
            onClick = { permissionsManager.requestPermission(context, PermissionGroup.Calendar) },
        )
    } else if (calendars != null) {
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
                painter = painterResource(R.drawable.help_20px), contentDescription = null
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
                icon = { Icon(painterResource(R.drawable.link_off_24px), null) },
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
                icon = R.drawable.link_24px,
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