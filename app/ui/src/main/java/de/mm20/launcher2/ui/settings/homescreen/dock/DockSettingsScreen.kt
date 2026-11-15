package de.mm20.launcher2.ui.settings.homescreen.dock

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.preferences.DockItem
import de.mm20.launcher2.preferences.ui.ShutterSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.component.preferences.*
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppWidgetConfig
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data object DockSettingsRoute : NavKey

@Composable
fun DockSettingsScreen() {
    val viewModel: DockSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val dockPages by viewModel.dockPages.collectAsStateWithLifecycle()
    val rows by viewModel.dockRows.collectAsStateWithLifecycle()
    val columns by viewModel.dockColumns.collectAsStateWithLifecycle()
    val defaultPage by viewModel.defaultPage.collectAsStateWithLifecycle()
    val dockBackgroundEnabled by viewModel.dockBackgroundEnabled.collectAsStateWithLifecycle()
    val dockBackgroundColor by viewModel.dockBackgroundColor.collectAsStateWithLifecycle()
    val dockBackgroundOpacity by viewModel.dockBackgroundOpacity.collectAsStateWithLifecycle()
    val dockBackgroundShadow by viewModel.dockBackgroundShadow.collectAsStateWithLifecycle()
    val dockPageIndicatorEnabled by viewModel.dockPageIndicatorEnabled.collectAsStateWithLifecycle()
    val dockPageIndicatorColor by viewModel.dockPageIndicatorColor.collectAsStateWithLifecycle()
    val grid by viewModel.gridSettings.collectAsStateWithLifecycle()
    val dockGrid by viewModel.dockGrid.collectAsStateWithLifecycle()

    val shutterSettings = koinInject<ShutterSettings>()
    val shuttersEnabled by shutterSettings.enabled.collectAsStateWithLifecycle(true)

    var showSearchablePicker by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showChoiceDialog by remember { mutableStateOf(false) }
    var pendingSlotFilled by remember { mutableStateOf(false) }

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_dock)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_dock_pages)) {
                if (dockPages.isEmpty()) {
                    Preference(
                        title = stringResource(I18nR.string.preference_dock_custom_enable),
                        summary = stringResource(I18nR.string.preference_dock_custom_enable_summary),
                        onClick = {
                            viewModel.enableCustomDock()
                        }
                    )
                } else {
                    val pagerState = rememberPagerState { dockPages.size }

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        ) { pageIndex ->
                            val pageItems = dockPages[pageIndex]
                            val gridState = rememberLazyGridState()
                            val dragAndDropState = rememberLazyDragAndDropGridState(
                                gridState = gridState,
                                onItemMove = { from, to ->
                                    viewModel.moveItem(pageIndex, from.index, to.index)
                                }
                            )

                            LazyVerticalDragAndDropGrid(
                                state = dragAndDropState,
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.Center,
                                userScrollEnabled = false
                            ) {
                                items(rows * columns, key = { it }) { index ->
                                    val item = pageItems.getOrNull(index)
                                    DraggableItem(state = dragAndDropState, key = index) { isDragged ->
                                        DockSlot(
                                            item = item,
                                            viewModel = viewModel,
                                            isDragged = isDragged,
                                            onClick = {
                                                viewModel.pendingItemPos = Triple(pageIndex, index / columns, index % columns)
                                                pendingSlotFilled = item != null &&
                                                    !(item is DockItem.Searchable && item.key.isEmpty())
                                                showChoiceDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Page number + default-page picker only make sense with more than one
                        // dock page - with a single page it's always page 1/1 and always default,
                        // so showing it is just dead chrome taking up vertical space.
                        if (dockPages.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${dockPages.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = defaultPage == pagerState.currentPage,
                                        onClick = { viewModel.setDefaultPage(pagerState.currentPage) }
                                    )
                                    Text(stringResource(I18nR.string.preference_dock_page_default), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            PreferenceCategory {
                SliderPreference(
                    title = stringResource(I18nR.string.preference_clockwidget_dock_rows),
                    value = rows,
                    min = 1,
                    max = 4,
                    onValueChanged = { viewModel.setRows(it) }
                )
                SliderPreference(
                    title = stringResource(I18nR.string.preference_dock_columns),
                    value = columns,
                    min = 1,
                    max = 10,
                    onValueChanged = { viewModel.setColumns(it) }
                )
            }
        }

        if (dockPages.isNotEmpty()) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.preference_dock_multiple)) {
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_dock_multiple_enable),
                        summary = stringResource(I18nR.string.preference_dock_multiple_enable_summary),
                        value = dockPages.size > 1,
                        onValueChanged = { enabled ->
                            viewModel.setDockCount(if (enabled) 2 else 1)
                        }
                    )
                    AnimatedVisibility(dockPages.size > 1) {
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_dock_count),
                            value = dockPages.size,
                            min = 2,
                            max = DockSettingsScreenVM.MAX_DOCKS,
                            onValueChanged = { viewModel.setDockCount(it) }
                        )
                    }
                }
            }
        }

        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_category_grid_dock)) {
                var override by remember { mutableStateOf(false) }
                LaunchedEffect(dockGrid, grid) {
                    // Only ever switch the toggle ON from stored values; turning it off is the
                    // user's decision (otherwise equal values would collapse the sliders mid-drag).
                    if (dockGrid != null && grid != null &&
                        dockGrid!!.iconSize != grid!!.iconSize
                    ) override = true
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_dock_grid_override),
                    value = override,
                    onValueChanged = {
                        override = it
                        if (!it) {
                            viewModel.setDockGridColumnCount(null)
                            viewModel.setDockGridIconSize(null)
                        } else {
                            grid?.let { g ->
                                viewModel.setDockGridIconSize(g.iconSize)
                            }
                        }
                    }
                )
                AnimatedVisibility(override && dockGrid != null) {
                    Column {
                        // Column count is not offered here: the dock's columns come from the
                        // "Dock columns" slider above, a separate grid column override had no effect.
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_grid_icon_size),
                            value = dockGrid?.iconSize ?: 48,
                            step = 8,
                            min = 32,
                            max = 64,
                            onValueChanged = { viewModel.setDockGridIconSize(it) }
                        )
                    }
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_dock_background),
                    summary = stringResource(I18nR.string.preference_dock_background_summary),
                    value = dockBackgroundEnabled,
                    onValueChanged = { viewModel.setDockBackgroundEnabled(it) }
                )
                AnimatedVisibility(dockBackgroundEnabled) {
                    Column {
                        ColorPreference(
                            title = stringResource(I18nR.string.preference_dock_background_color),
                            value = dockBackgroundColor?.let { Color(it) },
                            onValueChanged = { viewModel.setDockBackgroundColor(it?.toArgb()) }
                        )
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_dock_background_opacity),
                            value = (dockBackgroundOpacity * 100).toInt(),
                            min = 0,
                            max = 100,
                            onValueChanged = { viewModel.setDockBackgroundOpacity(it / 100f) }
                        )
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_dock_background_shadow),
                            value = dockBackgroundShadow,
                            min = 0,
                            max = 16,
                            onValueChanged = { viewModel.setDockBackgroundShadow(it) }
                        )
                    }
                }
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_dock_page_indicator),
                    summary = stringResource(I18nR.string.preference_dock_page_indicator_summary),
                    value = dockPageIndicatorEnabled,
                    onValueChanged = { viewModel.setDockPageIndicatorEnabled(it) }
                )
                AnimatedVisibility(dockPageIndicatorEnabled) {
                    ColorPreference(
                        title = stringResource(I18nR.string.preference_dock_page_indicator_color),
                        value = dockPageIndicatorColor?.let { Color(it) },
                        onValueChanged = { viewModel.setDockPageIndicatorColor(it?.toArgb()) }
                    )
                }
            }
        }

        item {
            PreferenceCategory(
                title = stringResource(I18nR.string.preference_category_shutters),
            ) {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_shutters),
                    summary = stringResource(I18nR.string.preference_shutters_summary),
                    value = shuttersEnabled,
                    onValueChanged = {
                        shutterSettings.setEnabled(it)
                    }
                )
            }
        }
    }

    if (showChoiceDialog) {
        AlertDialog(
            containerColor = de.mm20.launcher2.ui.component.virtualAppPopup(),
            onDismissRequest = { showChoiceDialog = false },
            // === TELOS_PENDING_REVIEW_START: ui_i18n_and_features_batch ===
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dock_pick_item_type))  } },
            text = { Text(stringResource(I18nR.string.dock_pick_item_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showChoiceDialog = false
                    showSearchablePicker = true
                }) {
                    Text(stringResource(I18nR.string.dock_item_app))
                }
            },
            dismissButton = {
                Row {
                    if (pendingSlotFilled) {
                        TextButton(onClick = {
                            showChoiceDialog = false
                            viewModel.pendingItemPos?.let { viewModel.setItem(it.first, it.second, it.third, null) }
                        }) {
                            Text(stringResource(I18nR.string.dock_menu_remove))
                        }
                    }
                    TextButton(onClick = {
                        showChoiceDialog = false
                        showWidgetPicker = true
                    }) {
                        Text(stringResource(I18nR.string.dock_item_widget))
                    }
                }
            }
            // === TELOS_PENDING_REVIEW_END: ui_i18n_and_features_batch ===
        )
    }

    if (showSearchablePicker) {
        DismissableBottomSheet(
            expanded = true,
            onDismissRequest = { showSearchablePicker = false }
        ) {
            SearchablePicker(
                applicationsOnly = true,
                modifier = Modifier.fillMaxWidth().height(400.dp),
                value = null,
                onValueChanged = { searchable ->
                    val pos = viewModel.pendingItemPos
                    if (pos != null && searchable != null) {
                        viewModel.setItem(pos.first, pos.second, pos.third, DockItem.Searchable(searchable.key), searchable)
                    }
                    showSearchablePicker = false
                }
            )
        }
    }

    if (showWidgetPicker) {
        WidgetPickerSheet(
            expanded = true,
            includeBuiltinWidgets = false,
            filter1x1 = true,
            onWidgetSelected = { widget ->
                if (widget is AppWidget) {
                    val pos = viewModel.pendingItemPos
                    if (pos != null) {
                        val manager = AppWidgetManager.getInstance(context)
                        val info = manager.getAppWidgetInfo(widget.config.widgetId)
                        if (info != null) {
                            viewModel.setItem(
                                pos.first, pos.second, pos.third,
                                DockItem.Widget(
                                    widget.config.widgetId,
                                    info.provider.packageName,
                                    info.provider.className
                                )
                            )
                        }
                    }
                }
                showWidgetPicker = false
            },
            onDismiss = { showWidgetPicker = false }
        )
    }
}

@Composable
fun DockSlot(
    item: DockItem?,
    viewModel: DockSettingsScreenVM,
    isDragged: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resolvedSearchable by remember(item) {
        if (item is DockItem.Searchable && item.key.isNotEmpty()) {
            viewModel.getSearchable(item.key)
        } else {
            flowOf(null)
        }
    }.collectAsStateWithLifecycle(null)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (isDragged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (item is DockItem.Widget) {
            val widgetInfo = remember(item.widgetId) {
                AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(item.widgetId)
            }
            if (widgetInfo != null) {
                AsyncImage(
                    model = widgetInfo.loadIcon(context, 48.dp.toPixels().toInt()),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(painterResource(BaseR.drawable.widgets_24px), contentDescription = null)
            }
        } else if (resolvedSearchable != null) {
            val iconSize = 48.dp.toPixels().toInt()
            val icon by remember(resolvedSearchable!!.key) {
                viewModel.getIcon(resolvedSearchable!!, iconSize)
            }.collectAsStateWithLifecycle(null)

            ShapedLauncherIcon(
                icon = { icon ?: resolvedSearchable!!.getPlaceholderIcon(context) },
                size = 32.dp
            )
        } else {
            Icon(
                painterResource(BaseR.drawable.add_24px), 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
