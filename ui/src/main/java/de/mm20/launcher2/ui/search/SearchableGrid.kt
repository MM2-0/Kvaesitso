package de.mm20.launcher2.ui.search

import android.view.ViewGroup
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.google.accompanist.insets.LocalWindowInsets
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.component.SectionDivider
import de.mm20.launcher2.ui.ktx.toDp
import de.mm20.launcher2.ui.legacy.search.SearchGridView
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.toPixels

fun LazyListScope.LegacySearchableGrid(
    items: List<Searchable>,
    columns: Int = 5,
    listState: LazyListState
) {
    item {

        AndroidView(
            {
                SearchGridView(it).apply {
                    columnCount = columns
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }, modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .animateContentSize()
        ) {
            it.submitItems(items)
        }
    }
    if (items.isNotEmpty()) {
        SectionDivider()
    }
}

@OptIn(ExperimentalAnimationApi::class)
fun LazyListScope.NotSoLazySearchableGrid(
    items: List<Searchable>,
    columns: Int = 5,
    listState: LazyListState
) {
    val rows = (items.size + columns - 1) / columns
    item {
        for (rowIndex in 0 until rows) {
            var focusedItem by remember { mutableStateOf(-1) }
            if (focusedItem != -1 && listState.isScrollInProgress) focusedItem = -1

            Row(
                modifier = Modifier
                    .requiredHeight(100.dp)
                    .zIndex(
                        animateFloatAsState(
                            if (focusedItem != -1 && rowIndex == focusedItem / columns) 100f else 0f
                        ).value
                    )
            ) {
                for (colIndex in 0 until columns) {
                    val itemIndex = rowIndex * columns + colIndex
                    if (itemIndex < items.size) {
                        GridItem(
                            item = items[itemIndex],
                            column = colIndex,
                            totalColumns = columns,
                            hasFocus = itemIndex == focusedItem,
                            requestFocus = {
                                focusedItem = if (it) itemIndex else -1
                            })
                    } else {
                        Spacer(Modifier.weight(1f, fill = true))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
fun LazyListScope.SearchableGrid(
    items: List<Searchable>,
    columns: Int = 5,
    listState: LazyListState
) {
    val rows = (items.size + columns - 1) / columns

    items(rows) { rowIndex ->
        var focusedItem by remember { mutableStateOf(-1) }
        if (focusedItem != -1 && listState.isScrollInProgress) focusedItem = -1

        Row(
            modifier = Modifier
                .requiredHeight(100.dp)
                .zIndex(
                    animateFloatAsState(
                        if (focusedItem != -1 && rowIndex == focusedItem / columns) 100f else 0f
                    ).value
                )
        ) {
            for (colIndex in 0 until columns) {
                val itemIndex = rowIndex * columns + colIndex
                if (itemIndex < items.size) {
                    GridItem(
                        item = items[itemIndex],
                        column = colIndex,
                        totalColumns = columns,
                        hasFocus = itemIndex == focusedItem,
                        requestFocus = {
                            focusedItem = if (it) itemIndex else -1
                        })
                } else {
                    Spacer(Modifier.weight(1f, fill = true))
                }
            }
        }
    }

    if (items.isNotEmpty()) {
        SectionDivider()
    }
}

@Composable
fun RowScope.GridItem(
    item: Searchable,
    column: Int,
    totalColumns: Int,
    hasFocus: Boolean,
    requestFocus: (Boolean) -> Unit
) {
    val insets = LocalWindowInsets.current.systemBars

    val topSpace = insets.top + 64.dp.toPixels()

    val gridWidth =
        LocalWindowSize.current.width.toDp() - 16.dp - (insets.left + insets.right).toDp()
    val representation = if (hasFocus) Representation.Full else Representation.Grid


    val offsetX by animateDpAsState(
        if (representation == Representation.Grid) 0.dp
        else gridWidth / totalColumns * ((totalColumns - 1) / 2 - column)
    )

    var calculatedYOffset by remember { mutableStateOf(0f) }
    val offsetY by animateDpAsState(
        if (representation == Representation.Grid) 0.dp
        else calculatedYOffset.toDp()
    )

    val width by animateDpAsState(
        if (representation == Representation.Grid) gridWidth / totalColumns
        else gridWidth
    )
    val z by animateFloatAsState(
        if (representation == Representation.Grid) 0f
        else 100f
    )

    val windowSize = LocalWindowSize.current

    Box(
        modifier = Modifier
            .weight(1f, fill = true)
            .fillMaxHeight()
            .zIndex(z)
            .onGloballyPositioned {

                calculatedYOffset = if (representation == Representation.Full) {
                    val position = it.positionInWindow()
                    val size = it.size
                    val topOffset = -position.y + topSpace + size.height / 2
                    if (topOffset > 0) {
                        topOffset
                    } else {
                        val bottom = position.y + size.height
                        val bottomOffset =
                            -(bottom - windowSize.height) - size.height / 2 - insets.bottom
                        if (bottomOffset < 0) {
                            bottomOffset
                        } else {
                            0f
                        }
                    }
                } else {
                    0f
                }
            }
    ) {
        key(item.key) {
            CompositionLocalProvider(LocalGridColumnWidth provides width) {
                SearchableItem(
                    item = item,
                    modifier = Modifier
                        .offset(offsetX, offsetY)
                        .requiredWidth(width)
                        .wrapContentHeight(unbounded = true)
                        .align(Alignment.BottomCenter),
                    representation = representation,
                    initialRepresentation = Representation.Grid,
                    onRepresentationChange = {
                        requestFocus(it == Representation.Full)
                    }
                )
            }
        }
    }
}

val LocalGridColumnWidth = compositionLocalOf { 0.dp }