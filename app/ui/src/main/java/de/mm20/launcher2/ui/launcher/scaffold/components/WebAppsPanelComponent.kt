package de.mm20.launcher2.ui.launcher.scaffold.components

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR
import de.mm20.launcher2.icons.TelosIcons

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.scaffold.LauncherScaffoldState
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.settings.webapps.EditWebAppShortcutSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * A dedicated grid of web app shortcuts, reached via whichever gesture slot has
 * [de.mm20.launcher2.preferences.GestureAction.WebAppsPanel] assigned - separate from the
 * Floating Launcher sidebar, which stays app-shortcut-only. Reuses [GridItem] (icon loading, tap
 * to launch, long-press popup) for each cell and [LazyVerticalDragAndDropGrid] for in-panel
 * reordering, matching the Widgets screen's own edit-mode pattern.
 */
internal object WebAppsPanelComponent : ScaffoldComponent() {

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        val viewModel: WebAppsPanelVM = viewModel()
        val items by viewModel.items.collectAsStateWithLifecycle()
        val sections by viewModel.sections.collectAsStateWithLifecycle()
        val availableToAdd by viewModel.availableToAdd.collectAsStateWithLifecycle()
        var editMode by rememberSaveable { mutableStateOf(false) }
        var showCreateSheet by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val columns = LocalGridSettings.current.columnCount.coerceAtLeast(2)

        val gridState = rememberLazyDragAndDropGridState(
            onDragStart = { editMode },
        ) { from, to ->
            viewModel.moveItem(from.index, to.index)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            LazyVerticalDragAndDropGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = if (editMode) 104.dp else 8.dp, bottom = 8.dp),
            ) {
                // Group section headers only make sense for the plain browsing grid - edit mode's
                // drag-and-drop reorder operates on flat panel order, so it always uses the flat
                // list regardless of grouping (matches setOrder's own flat-index contract).
                val gridSections = sections
                if (!editMode && gridSections != null) {
                    for (section in gridSections) {
                        item(
                            key = "group-${section.group?.id ?: "ungrouped"}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                val categoryIcon = de.mm20.launcher2.ui.settings.webapps.WebAppPresets
                                    .byId(section.group?.category)?.iconRes
                                if (categoryIcon != null) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(categoryIcon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = section.group?.name
                                        ?: stringResource(I18nR.string.web_apps_panel_ungrouped),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            section.items.size,
                            key = { section.items[it].key },
                        ) { i ->
                            val shortcut = section.items[i]
                            GridItem(
                                item = shortcut,
                                showLabels = true,
                            )
                        }
                    }
                } else {
                    items(
                        items.size,
                        key = { items[it].key },
                    ) { i ->
                        val shortcut = items[i]
                        DraggableItem(state = gridState, key = shortcut.key) { _ ->
                            Box {
                                GridItem(
                                    item = shortcut,
                                    showLabels = true,
                                )
                                if (editMode) {
                                    IconButton(
                                        onClick = { viewModel.remove(shortcut) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp),
                                    ) {
                                        Icon(
                                            painterResource(BaseR.drawable.close_24px),
                                            contentDescription = stringResource(I18nR.string.widget_action_remove),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (editMode) {
                    if (availableToAdd.isNotEmpty()) {
                        item(key = "available-header", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(I18nR.string.web_apps_panel_add_existing),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                        items(
                            availableToAdd.size,
                            key = { "available-" + availableToAdd[it].key },
                        ) { i ->
                            val shortcut = availableToAdd[i]
                            AvailableWebAppTile(
                                shortcut = shortcut,
                                onClick = { viewModel.addExisting(shortcut) },
                            )
                        }
                    }
                    item(key = "add", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { showCreateSheet = true }) {
                                Icon(
                                    painterResource(BaseR.drawable.add_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    stringResource(I18nR.string.web_app_shortcut_create),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (!editMode && items.isEmpty()) {
                Text(
                    text = stringResource(I18nR.string.web_apps_panel_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )
            }
            if (editMode) {
                BackHandler {
                    editMode = false
                    scope.launch { state.unlock() }
                }
            }
            AnimatedVisibility(
                editMode,
                modifier = Modifier.zIndex(10f),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                CenterAlignedTopAppBar(
                    title = { TelosCyberHeading {  Text(stringResource(I18nR.string.web_apps_panel_edit))  } },
                    navigationIcon = {
                        IconButton(onClick = {
                            editMode = false
                            scope.launch { state.unlock() }
                        }) {
                            Icon(TelosIcons.ArrowBack, stringResource(I18nR.string.action_done))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
            if (!editMode) {
                val uiSettings: de.mm20.launcher2.preferences.ui.UiSettings = koinInject()
                val searchBarStyle by uiSettings.searchBarStyle.collectAsState(de.mm20.launcher2.preferences.SearchBarStyle.Transparent)
                val bottomSearchBar by uiSettings.bottomSearchBar.collectAsState(false)
                val readability = de.mm20.launcher2.ui.theme.rememberWallpaperContentStyle()

                IconButton(
                    onClick = {
                        editMode = true
                        scope.launch { state.lock(hideSearchBar = true) }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = if (!bottomSearchBar && searchBarStyle != de.mm20.launcher2.preferences.SearchBarStyle.Hidden) de.mm20.launcher2.ui.locals.LocalSearchBarHeight.current + 8.dp else 0.dp)
                        .then(readability.modifier),
                ) {
                    Icon(
                        painterResource(BaseR.drawable.tune_24px),
                        contentDescription = stringResource(I18nR.string.web_apps_panel_edit),
                        tint = readability.color
                    )
                }
            }
        }

        EditWebAppShortcutSheet(
            expanded = showCreateSheet,
            existing = null,
            onSave = { label, url, iconUri, faviconUrl, rendererPackage, _, _, _, customCss, notificationsEnabled, _, adBlockMode, cookieOptions ->
                viewModel.createAndAdd(label, url, iconUri, faviconUrl, rendererPackage, customCss, notificationsEnabled, adBlockMode, cookieOptions)
                showCreateSheet = false
            },
            onDismiss = { showCreateSheet = false },
            onImportIcon = { uri, sizePx -> viewModel.importIcon(uri, sizePx) },
            onFindFavicon = { url -> viewModel.findFavicon(url) },
            onExportIconPackIcon = { customIcon, sizePx -> viewModel.exportIconPackIcon(customIcon, sizePx) },
        )
    }
}

/**
 * A tap-to-add tile for the edit mode's "add existing" section. Deliberately NOT [GridItem]:
 * that composable carries its own tap-to-launch handling, which would consume the tap before
 * this tile's own click listener ever saw it - tapping an icon here must add it to the panel,
 * not open the web app.
 */
@Composable
private fun AvailableWebAppTile(
    shortcut: WebAppShortcut,
    onClick: () -> Unit,
) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${shortcut.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()

    LaunchedEffect(shortcut, iconSize) {
        viewModel.init(shortcut, iconSize.toInt())
    }

    val icon by viewModel.icon.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(modifier = Modifier.alpha(0.55f)) {
                ShapedLauncherIcon(
                    size = LocalGridSettings.current.iconSize.dp,
                    icon = { icon },
                )
            }
            Icon(
                painterResource(BaseR.drawable.add_24px),
                contentDescription = stringResource(I18nR.string.web_apps_panel_add_existing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
            )
        }
        Text(
            text = shortcut.labelOverride ?: shortcut.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 4.dp)
                .alpha(0.7f),
        )
    }
}
