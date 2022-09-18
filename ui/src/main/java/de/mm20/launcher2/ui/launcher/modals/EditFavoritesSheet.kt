package de.mm20.launcher2.ui.launcher.modals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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

    val items by viewModel.gridItems.observeAsState(emptyList())
    val loading by viewModel.loading.observeAsState(true)

    val columns = LocalGridColumns.current

    val state = rememberLazyDragAndDropGridState(
        onDragStart = {
            items.getOrNull(it.index) is FavoritesSheetGridItem.Favorite
        }
    ) { from, to ->
        viewModel.moveItem(from, to)
    }

    val iconSize = 48.dp.toPixels()

    BottomSheetDialog(onDismissRequest = onDismiss, title = { /*TODO*/ }) {
        if (loading) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
        } else {
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
                        }
                    }
                    is FavoritesSheetGridItem.Divider -> {
                        Text(
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            text = stringResource(id = it.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
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

sealed interface FavoritesSheetGridItem {
    class Favorite(val item: Searchable) : FavoritesSheetGridItem
    class Divider(val titleRes: Int) : FavoritesSheetGridItem
    class Spacer(val span: Int = 1) : FavoritesSheetGridItem
    class EmptySection() : FavoritesSheetGridItem
    class Tags() : FavoritesSheetGridItem
}