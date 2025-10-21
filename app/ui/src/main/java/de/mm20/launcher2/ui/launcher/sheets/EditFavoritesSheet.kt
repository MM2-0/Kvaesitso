package de.mm20.launcher2.ui.launcher.sheets

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.TagChip
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyDragAndDropRow
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropListState
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalGridSettings
import kotlin.math.roundToInt

@Composable
fun EditFavoritesSheet(
    onDismiss: () -> Unit,
) {
    val viewModel: EditFavoritesSheetVM = viewModel()

    LaunchedEffect(null) {
        viewModel.reload()
    }

    val loading by viewModel.loading
    val createShortcutTarget by viewModel.createShortcutTarget

    BottomSheetDialog(onDismiss) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(it)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
        } else {
            ReorderFavoritesGrid(viewModel, it)
            if (createShortcutTarget != null) {
                BottomSheetDialog({viewModel.cancelPickShortcut()}) {
                    ShortcutPicker(viewModel, it)
                }
            }
        }
    }
}

@Composable
fun ReorderFavoritesGrid(viewModel: EditFavoritesSheetVM, paddingValues: PaddingValues) {
    val items by viewModel.gridItems
    val columns = LocalGridSettings.current.columnCount

    val availableTags by viewModel.availableTags
    val pinnedTags by viewModel.pinnedTags

    var contextMenuItemKey by remember { mutableStateOf<String?>(null) }

    val contextMenuCloseDistance = 8.dp.toPixels()

    var draggedItemKey by remember { mutableStateOf<String?>(null) }
    var hoveredTag by remember { mutableStateOf<String?>(null) }

    var createTag by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val tagsListState = rememberLazyListState()
    val tagsTitleSize = 48.dp.toPixels()
    val tagsSpacing = 12.dp.toPixels()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state = rememberLazyDragAndDropGridState(
        gridState = gridState,
        onDragStart = {
            val item = items.getOrNull(it.index)

            if (item !is FavoritesSheetGridItem.Favorite) return@rememberLazyDragAndDropGridState false

            draggedItemKey = item.item.key
            contextMenuItemKey = item.item.key
            true
        },
        onDrag = { item, offset, position ->
            if (offset.getDistanceSquared() > contextMenuCloseDistance) {
                contextMenuItemKey = null
            }
            val draggedCenter = Rect(position, item.size.toSize()).center
            val hoveredItem = gridState.layoutInfo.visibleItemsInfo.find {
                Rect(
                    it.offset.toOffset(),
                    it.size.toSize()
                ).contains(draggedCenter)
            }
            if (hoveredItem != null
                && items[hoveredItem.index] is FavoritesSheetGridItem.Tags
                && hoveredItem.offset.y + tagsTitleSize < position.y
            ) {
                val scroll = tagsListState.layoutInfo.viewportStartOffset
                val relCenter =
                    if (isRtl) draggedCenter.copy(x = tagsListState.layoutInfo.viewportSize.width - draggedCenter.x)
                    else draggedCenter
                val tag = tagsListState.layoutInfo.visibleItemsInfo.find {
                    relCenter.x + scroll > it.offset && relCenter.x + scroll < it.offset + it.size - tagsSpacing
                }
                hoveredTag = tag?.index?.let { pinnedTags[it].tag }
            } else {
                hoveredTag = null
            }
        },
        onDragEnd = {
            viewModel.addTag(draggedItemKey, hoveredTag)
            draggedItemKey = null
            hoveredTag = null
        },
        onDragCancel = {
            draggedItemKey = null
            hoveredTag = null
        }
    ) { from, to ->
        viewModel.moveItem(from, to)
    }

    val iconSize = 48.dp.toPixels()

    LazyVerticalDragAndDropGrid(
        state = state,
        columns = GridCells.Fixed(columns),
        contentPadding = paddingValues,

        ) {
        items(
            items.size,
            key = { i ->
                val it = items[i]
                if (it is FavoritesSheetGridItem.Favorite) it.item.key else i
            },
            span = { i ->
                val it = items[i]
                when (it) {
                    is FavoritesSheetGridItem.Favorite -> GridItemSpan(1)
                    is FavoritesSheetGridItem.Divider -> GridItemSpan(columns)
                    is FavoritesSheetGridItem.EmptySection -> GridItemSpan(columns)
                    is FavoritesSheetGridItem.Spacer -> GridItemSpan(it.span)
                    is FavoritesSheetGridItem.Tags -> GridItemSpan(columns)
                }
            }
        ) { i ->
            when (val it = items[i]) {
                is FavoritesSheetGridItem.Favorite -> {
                    val icon by remember(it.item.key) {
                        viewModel.getIcon(
                            it.item,
                            iconSize.roundToInt()
                        )
                    }.collectAsState(null)
                    val badge by remember(it.item.key) {
                        viewModel.getBadge(
                            it.item,
                        )
                    }.collectAsState(null)
                    DraggableItem(state = state, key = it.item.key) { dragged ->
                        GridItem(
                            label = it.item.labelOverride ?: it.item.label,
                            icon = icon,
                            badge = badge
                        )
                        if (contextMenuItemKey == it.item.key) {
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = { contextMenuItemKey = null }) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(stringResource(R.string.menu_remove))
                                    }, onClick = {
                                        contextMenuItemKey?.let { viewModel.remove(it) }
                                        contextMenuItemKey = null
                                    }
                                )
                            }
                        }
                    }
                }

                is FavoritesSheetGridItem.Divider -> {
                    val title = when (it.section) {
                        FavoritesSheetSection.ManuallySorted -> R.string.edit_favorites_dialog_pinned_sorted
                        FavoritesSheetSection.AutomaticallySorted -> R.string.edit_favorites_dialog_pinned_unsorted
                        FavoritesSheetSection.FrequentlyUsed -> R.string.edit_favorites_dialog_unpinned
                    }
                    var showSettings by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                text = stringResource(id = title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (it.section == FavoritesSheetSection.FrequentlyUsed) {
                                FilledTonalIconToggleButton(
                                    modifier = Modifier.offset(x = 4.dp),
                                    checked = showSettings,
                                    onCheckedChange = { showSettings = it }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                FilledTonalIconButton(
                                    modifier = Modifier.offset(x = 4.dp),
                                    onClick = {
                                        viewModel.pickShortcut(it.section)
                                    }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                        val enableFrequentlyUsed by viewModel.enableFrequentlyUsed.collectAsState()
                        val frequentlyUsedRows by viewModel.frequentlyUsedRows.collectAsState()
                        AnimatedVisibility(showSettings) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 16.dp),
                                            text = stringResource(R.string.frequently_used_show_in_favorites),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Switch(
                                            checked = enableFrequentlyUsed == true,
                                            onCheckedChange = {
                                                viewModel.setFrequentlyUsed(it)
                                            }
                                        )
                                    }
                                    AnimatedVisibility(enableFrequentlyUsed == true) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .padding(horizontal = 16.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text(
                                                modifier = Modifier.fillMaxWidth(),
                                                text = stringResource(R.string.frequently_used_rows),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Slider(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 16.dp),
                                                    value = frequentlyUsedRows.toFloat(),
                                                    colors = SliderDefaults.colors(
                                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    onValueChange = {
                                                        viewModel.setFrequentlyUsedRows(it.roundToInt())
                                                    },
                                                    steps = 2,
                                                    valueRange = 1f..4f
                                                )
                                                Text(
                                                    text = frequentlyUsedRows.toString(),
                                                    modifier = Modifier
                                                        .width(52.dp)
                                                        .padding(4.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                is FavoritesSheetGridItem.EmptySection -> {
                    val shape = MaterialTheme.shapes.medium
                    val color = MaterialTheme.colorScheme.outline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .drawBehind {
                                drawOutline(
                                    outline = shape.createOutline(
                                        size,
                                        layoutDirection,
                                        Density(density, fontScale)
                                    ),
                                    color = color,
                                    style = Stroke(
                                        2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(
                                            intervals = floatArrayOf(
                                                4.dp.toPx(),
                                                4.dp.toPx(),
                                            )
                                        )
                                    )
                                )
                            }
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 24.dp,
                                ),
                            text = stringResource(R.string.edit_favorites_dialog_empty_section),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                is FavoritesSheetGridItem.Spacer -> {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }

                is FavoritesSheetGridItem.Tags -> {
                    var showAddMenu by remember { mutableStateOf(false) }
                    Column {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                text = stringResource(R.string.edit_favorites_dialog_tags),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Box() {
                                FilledTonalIconButton(
                                    modifier = Modifier.offset(x = 4.dp),
                                    onClick = {
                                        showAddMenu = true
                                    }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false }) {
                                    for (tag in availableTags) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Tag, null)
                                            },
                                            text = { Text(tag.tag) },
                                            onClick = {
                                                viewModel.pinTag(tag)
                                                showAddMenu = false
                                            })
                                    }
                                    if (availableTags.isNotEmpty()) {
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Add, null)
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
                        if (pinnedTags.isNotEmpty()) {
                            val rowState = rememberLazyDragAndDropListState(
                                listState = tagsListState,
                            ) { from, to ->
                                viewModel.moveTag(from, to)
                            }
                            LazyDragAndDropRow(
                                modifier = Modifier.fillMaxWidth(),
                                state = rowState
                            ) {
                                items(
                                    pinnedTags,
                                    key = { it.key }
                                ) { tag ->
                                    DraggableItem(state = rowState, key = tag.key) { dragged ->

                                        TagChip(
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .pointerInput(null) {
                                                },
                                            tag = tag,
                                            selected = tag.tag == hoveredTag,
                                            dragged = dragged,
                                            clearable = true,
                                            onClear = {
                                                viewModel.unpinTag(tag)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                text = stringResource(R.string.edit_favorites_dialog_tag_section_empty),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
    if (createTag) {
        EditTagSheet(
            tag = null,
            onTagSaved = { tag ->
                viewModel.pinTag(Tag(tag))
            },
            onDismiss = {
                createTag = false
            }
        )
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: LauncherIcon?,
    badge: Badge?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShapedLauncherIcon(
            size = 48.dp,
            icon = { icon },
            badge = { badge })
        Text(
            label,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ShortcutPicker(viewModel: EditFavoritesSheetVM, paddingValues: PaddingValues) {

    val hasShortcutPermission by remember { viewModel.hasShortcutPermission }.collectAsState(null)

    val shortcutActivities by remember(hasShortcutPermission) { viewModel.getShortcutActivities() }.collectAsState(
        emptyList()
    )

    val context = LocalContext.current
    val activityLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode != Activity.RESULT_OK) {
                viewModel.cancelPickShortcut()
            }
            viewModel.createShortcut(context, it.data)

        }

    val activity = LocalLifecycleOwner.current as AppCompatActivity
    LazyColumn(
        contentPadding = paddingValues
    ) {
        if (hasShortcutPermission == false) {
            item {
                MissingPermissionBanner(
                    modifier = Modifier.padding(bottom = 16.dp),
                    text = stringResource(
                        R.string.missing_permission_appshortcuts_create,
                        stringResource(R.string.app_name)
                    ),
                    onClick = { viewModel.requestShortcutPermission(activity) })
            }
        }
        items(shortcutActivities) {
            val icon by remember(it) { it.getIcon(context) }.collectAsState(null)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = {
                    val intent = it.getIntent(context) ?: return@OutlinedCard run {
                        Log.e("MM20", "Couldn't get intent for shortcut")
                    }
                    activityLauncher.launch(IntentSenderRequest.Builder(intent).build(), null)
                }) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = it.label,
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

sealed interface FavoritesSheetGridItem {
    class Favorite(val item: SavableSearchable) : FavoritesSheetGridItem
    class Divider(val section: FavoritesSheetSection) : FavoritesSheetGridItem
    class Spacer(val span: Int = 1) : FavoritesSheetGridItem
    object EmptySection : FavoritesSheetGridItem
    object Tags : FavoritesSheetGridItem
}

enum class FavoritesSheetSection {
    ManuallySorted,
    AutomaticallySorted,
    FrequentlyUsed
}