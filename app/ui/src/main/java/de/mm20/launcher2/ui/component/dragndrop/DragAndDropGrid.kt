package de.mm20.launcher2.ui.component.dragndrop

import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.ktx.toIntOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.android.awaitFrame
import kotlin.coroutines.coroutineContext

/**
 * Create and remember a [LazyDragAndDropGridState]
 * @param gridState the [LazyGridState] to use with the LazyGrid
 * @param onDragStart callback that will be called when an item is picked up. If the return value
 * is false, the drag operation will be canceled.
 */
@Composable
fun rememberLazyDragAndDropGridState(
    gridState: LazyGridState = rememberLazyGridState(),
    onDragStart: (item: LazyGridItemInfo) -> Boolean = { true },
    onDrag: (item: LazyGridItemInfo, offset: Offset, position: Offset) -> Unit = {_, _, _ ->},
    onDragEnd: (item: LazyGridItemInfo) -> Unit = {},
    onDragCancel: (item: LazyGridItemInfo) -> Unit = {},
    onItemMove: (from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit
): LazyDragAndDropGridState {
    return remember {
        LazyDragAndDropGridState(
            gridState,
            onDragStart,
            onDrag,
            onDragEnd,
            onDragCancel,
            onItemMove
        )
    }
}

data class LazyDragAndDropGridState(
    val gridState: LazyGridState,
    val onDragStart: (item: LazyGridItemInfo) -> Boolean = { true },
    val onDrag: (item: LazyGridItemInfo, offset: Offset, position: Offset) -> Unit = {_, _, _ ->},
    val onDragEnd: (item: LazyGridItemInfo) -> Unit = {},
    val onDragCancel: (item: LazyGridItemInfo) -> Unit = {},
    val onItemMove: (from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit
) {
    var draggedItem by mutableStateOf<LazyGridItemInfo?>(null)
    var draggedItemAbsolutePosition by mutableStateOf<Offset?>(null)
    val draggedItemOffset by derivedStateOf {
        val absPos = draggedItemAbsolutePosition ?: return@derivedStateOf null
        val key = draggedItem?.key ?: return@derivedStateOf null
        val draggedItem = gridState.layoutInfo.visibleItemsInfo.find {
            it.key == key
        } ?: return@derivedStateOf null
        return@derivedStateOf absPos - draggedItem.offset.toOffset()
    }

    var droppedItemKey by mutableStateOf<Any?>(null)
    val droppedItemOffset = mutableStateOf<IntOffset>(IntOffset.Zero)

    private var currentDropPosition: Int? = null
    private var dropJob: Job? = null

    fun startDrag(draggedItem: LazyGridItemInfo): Boolean {
        if (!onDragStart(draggedItem)) return false
        this.draggedItem = draggedItem
        draggedItemAbsolutePosition = draggedItem.offset.toOffset()
        return true
    }

    /**
     * Move the currently dragged item to the specified drop target if the dragged item is held
     * for at least 300ms over the drop target. The move operation is canceled if the dragged item
     * is released or moved out of the dropTarget area during the 300ms time frame.
     */
    suspend fun attemptMove(dropTarget: LazyGridItemInfo) {
        if (currentDropPosition != dropTarget.index) {
            coroutineScope {
                dropJob?.cancelAndJoin()
                dropJob = launch {
                    currentDropPosition = dropTarget.index
                    delay(300)
                    // Get a fresh copy of layout info because index in saved layout info might be outdated
                    val dragged =
                        gridState.layoutInfo.visibleItemsInfo.find { it.key == draggedItem?.key }
                    if (dragged != null) {
                        onItemMove(dragged, dropTarget)
                    }
                }
            }
        }
    }

    suspend fun cancelDrag() {
        draggedItem?.let { onDragCancel(it) }
        afterDragEnded()
    }

    suspend fun endDrag() {
        draggedItem?.let { onDragEnd(it) }
        afterDragEnded()
    }

    private suspend fun afterDragEnded() {
        endScrolling()
        val key = draggedItem?.key
        val startOffset = draggedItemOffset
        draggedItem = null
        draggedItemAbsolutePosition = null
        currentDropPosition = null

        if (key == null || startOffset == null) return
        droppedItemKey = key
        droppedItemOffset.value = startOffset.toIntOffset()
        droppedItemOffset.animateTo(IntOffset.Zero, IntOffset.VectorConverter)
        droppedItemKey = null
    }

    private var scrollJob: Job? = null
    private var currentScrollDelta = 0.0f

    /**
     * Scroll the lazy grid by `delta` px per second until [endScrolling] is called
     */
    suspend fun enableScrolling(delta: Float) {
        if (currentScrollDelta == delta) return
        coroutineScope {
            scrollJob?.cancelAndJoin()
            scrollJob = launch {
                currentScrollDelta = delta
                delay(500)
                var lastFrame = awaitFrame()
                while (isActive) {
                    val frame = awaitFrame()
                    val timeDelta = frame - lastFrame
                    gridState.scrollBy(delta * timeDelta / 1000_000_000f)
                    lastFrame = frame
                }
            }
        }
    }

    /**
     * Cancel scrolling
     */
    fun endScrolling() {
        currentScrollDelta = 0f
        scrollJob?.cancel()
        scrollJob = null
    }
}

@Composable
fun LazyVerticalDragAndDropGrid(
    state: LazyDragAndDropGridState,
    columns: GridCells,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns,
        modifier.dragAndDrop(
            state,
            LocalHapticFeedback.current
        ),
        state.gridState,
        contentPadding,
        false,
        verticalArrangement,
        horizontalArrangement,
        flingBehavior,
        userScrollEnabled,
        content = content,
    )
}

@Composable
fun LazyHorizontalDragAndDropGrid(
    state: LazyDragAndDropGridState,
    rows: GridCells,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit
) {
    LazyHorizontalGrid(
        rows,
        modifier.dragAndDrop(
            state,
            LocalHapticFeedback.current
        ),
        state.gridState,
        contentPadding,
        false,
        horizontalArrangement,
        verticalArrangement,
        flingBehavior,
        userScrollEnabled,
        content = content,
    )
}

fun Modifier.dragAndDrop(
    state: LazyDragAndDropGridState,
    hapticFeedback: HapticFeedback
) =
    this then pointerInput(null) {
        val scope = CoroutineScope(coroutineContext)
        val scrollEdgeSize = 32.dp.toPx()
        val scrollDelta = 128.dp.toPx()
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val draggedItem = state.gridState.layoutInfo.visibleItemsInfo.find {
                    Rect(
                        it.offset.toOffset(),
                        it.size.toSize()
                    ).contains(offset)
                }
                if (draggedItem != null && state.startDrag(draggedItem)) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, dragAmount ->
                val absPosition = state.draggedItemAbsolutePosition
                val draggedItem = state.draggedItem
                if (absPosition != null && draggedItem != null) {
                    state.draggedItemAbsolutePosition = absPosition + dragAmount
                    val draggedCenter = Rect(absPosition, draggedItem.size.toSize()).center
                    val dragOver = state.gridState.layoutInfo.visibleItemsInfo.find {
                        Rect(
                            it.offset.toOffset(),
                            it.size.toSize()
                        ).contains(draggedCenter)
                    }

                    if (dragOver != null && dragOver.key != draggedItem.key) {
                        scope.launch {
                            state.attemptMove(dragOver)
                        }
                    }

                    val toStart =
                        if (state.gridState.layoutInfo.orientation == Orientation.Horizontal) draggedCenter.x else draggedCenter.y


                    if (toStart - state.gridState.layoutInfo.viewportStartOffset < scrollEdgeSize) {
                        scope.launch {
                            state.enableScrolling(-scrollDelta)
                        }
                    } else if (state.gridState.layoutInfo.viewportEndOffset - toStart < scrollEdgeSize) {
                        scope.launch {
                            state.enableScrolling(scrollDelta)
                        }
                    } else {
                        state.endScrolling()
                    }

                    state.draggedItemOffset?.let { state.onDrag(draggedItem, it, absPosition) }
                }
            },
            onDragCancel = {
                scope.launch { state.cancelDrag() }
            },
            onDragEnd = {
                scope.launch { state.endDrag() }
            },
        )
    }

@Composable
fun LazyGridItemScope.DraggableItem(
    modifier: Modifier = Modifier,
    state: LazyDragAndDropGridState,
    key: Any?,
    content: @Composable BoxScope.(isDragged: Boolean) -> Unit
) {
    val isDragged = state.draggedItem?.key == key || state.droppedItemKey == key
    Box(
        modifier = modifier
            .then(if (isDragged) Modifier else Modifier.animateItem())
            .zIndex(if (isDragged) 1f else 0f)
            .absoluteOffset {
                if (state.draggedItem?.key == key) {
                    state.draggedItemOffset?.toIntOffset() ?: IntOffset.Zero
                } else if (state.droppedItemKey == key) {
                    state.droppedItemOffset.value
                } else {
                    IntOffset.Zero
                }
            },
        content = { content(isDragged) },
    )
}
