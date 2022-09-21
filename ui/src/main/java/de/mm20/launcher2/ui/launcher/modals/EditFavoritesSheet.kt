package de.mm20.launcher2.ui.launcher.modals

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.helper.DraggableItem
import de.mm20.launcher2.ui.launcher.helper.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.launcher.helper.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.locals.LocalGridColumns
import kotlin.math.roundToInt

@Composable
fun EditFavoritesSheet(
    onDismiss: () -> Unit,
) {
    val viewModel: EditFavoritesSheetVM = viewModel()

    LaunchedEffect(null) {
        viewModel.reload()
    }

    val loading by viewModel.loading.observeAsState(true)
    val createShortcutTarget by viewModel.createShortcutTarget.observeAsState(null)

    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (createShortcutTarget == null) {
                    stringResource(id = R.string.menu_item_edit_favs)
                } else {
                    stringResource(id = R.string.create_app_shortcut)
                }
            )
        },
        swipeToDismiss = {
            createShortcutTarget == null
        },
        dismissOnBackPress = {
            createShortcutTarget == null
        },
        confirmButton = {
            if (createShortcutTarget != null) {
                OutlinedButton(onClick = { viewModel.cancelPickShortcut() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            } else {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
    ) {
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )
            }
        } else if (createShortcutTarget != null) {
            ShortcutPicker(viewModel)
        } else {
            ReorderFavoritesGrid(viewModel)
        }
    }
}

@Composable
fun ReorderFavoritesGrid(viewModel: EditFavoritesSheetVM) {
    val items by viewModel.gridItems.observeAsState(emptyList())
    val columns = LocalGridColumns.current

    var contextMenuItemKey by remember { mutableStateOf<String?>(null) }

    val contextMenuCloseDistance = 8.dp.toPixels()

    val state = rememberLazyDragAndDropGridState(
        onDragStart = {
            val item = items.getOrNull(it.index)

            if (item !is FavoritesSheetGridItem.Favorite) return@rememberLazyDragAndDropGridState false

            contextMenuItemKey = item.item.key
            true
        },
        onDrag = { _, offset ->
            if (offset.getDistanceSquared() > contextMenuCloseDistance) {
                contextMenuItemKey = null
            }
        }
    ) { from, to ->
        viewModel.moveItem(from, to)
    }

    val iconSize = 48.dp.toPixels()

    LazyVerticalDragAndDropGrid(
        state = state,
        columns = GridCells.Fixed(columns),

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
                        val enableFrequentlyUsed by viewModel.enableFrequentlyUsed.observeAsState(
                            null
                        )
                        val frequentlyUsedRows by viewModel.frequentlyUsedRows.observeAsState(1)
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
                                            text = "Show in favorites",
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
                                                text = "Number of rows",
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
                is FavoritesSheetGridItem.Tags -> {}
            }
        }
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
fun ShortcutPicker(viewModel: EditFavoritesSheetVM) {

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

    val iconSize = 48.dp.toPixels().roundToInt()
    val activity = LocalLifecycleOwner.current as AppCompatActivity
    LazyColumn {
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
            val icon by remember(it.key) { viewModel.getIcon(it, iconSize) }.collectAsState(null)
            val badge by remember(it.key) { viewModel.getBadge(it) }.collectAsState(null)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = {
                    val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val sender =
                        launcherApps.getShortcutConfigActivityIntent(it.launcherActivityInfo)
                            ?: return@OutlinedCard
                    activityLauncher.launch(IntentSenderRequest.Builder(sender).build(), null)
                }) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShapedLauncherIcon(
                        size = 48.dp,
                        icon = { icon },
                        badge = { badge },
                    )
                    Text(
                        text = it.labelOverride ?: it.label,
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

sealed interface FavoritesSheetGridItem {
    class Favorite(val item: Searchable) : FavoritesSheetGridItem
    class Divider(val section: FavoritesSheetSection) : FavoritesSheetGridItem
    class Spacer(val span: Int = 1) : FavoritesSheetGridItem
    object EmptySection : FavoritesSheetGridItem
    class Tags() : FavoritesSheetGridItem
}

enum class FavoritesSheetSection {
    ManuallySorted,
    AutomaticallySorted,
    FrequentlyUsed
}