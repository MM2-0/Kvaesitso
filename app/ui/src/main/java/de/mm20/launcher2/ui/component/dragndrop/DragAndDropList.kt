package de.mm20.launcher2.ui.component.dragndrop

import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import de.mm20.launcher2.ui.ktx.animateTo
import de.mm20.launcher2.ui.ktx.toIntOffset
import de.mm20.launcher2.ui.ktx.toPixels
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
fun rememberLazyDragAndDropListState(
    listState: LazyListState = rememberLazyListState(),
    scrollEdgeSize: Dp = 32.dp,
    scrollDelta: Dp = 128.dp,
    onDragStart: (item: LazyListItemInfo) -> Boolean = { true },
    onDrag: (item: LazyListItemInfo, offset: Offset) -> Unit = { _, _ -> },
    onDragEnd: (item: LazyListItemInfo) -> Unit = {},
    onDragCancel: (item: LazyListItemInfo) -> Unit = {},
    onItemMove: (from: LazyListItemInfo, to: LazyListItemInfo) -> Unit,
): LazyDragAndDropListState {
    val scrollDeltaPx = scrollDelta.toPixels()
    val scrollEdgeSizePx = scrollEdgeSize.toPixels()
    return remember {
        LazyDragAndDropListState(
            listState,
            onDragStart,
            onDrag,
            onDragEnd,
            onDragCancel,
            onItemMove,
            scrollDeltaPx,
            scrollEdgeSizePx,
        )
    }
}

data class LazyDragAndDropListState(
    val listState: LazyListState,
    val onDragStart: (item: LazyListItemInfo) -> Boolean = { true },
    val onDrag: (item: LazyListItemInfo, offset: Offset) -> Unit = { _, _ -> },
    val onDragEnd: (item: LazyListItemInfo) -> Unit = {},
    val onDragCancel: (item: LazyListItemInfo) -> Unit = {},
    val onItemMove: (from: LazyListItemInfo, to: LazyListItemInfo) -> Unit,
    private val scrollDelta: Float,
    private val scrollEdgeSize: Float,
) {
    var draggedItem by mutableStateOf<LazyListItemInfo?>(null)
    var draggedItemAbsolutePosition by mutableStateOf<Offset?>(null)
    val draggedItemOffset by derivedStateOf {
        val absPos = draggedItemAbsolutePosition ?: return@derivedStateOf null
        val key = draggedItem?.key ?: return@derivedStateOf null
        val draggedItem = listState.layoutInfo.visibleItemsInfo.find {
            it.key == key
        } ?: return@derivedStateOf null
        return@derivedStateOf absPos - draggedItem.offset.toOffset()
    }

    var droppedItemKey by mutableStateOf<Any?>(null)
    val droppedItemOffset = mutableStateOf<IntOffset>(IntOffset.Zero)

    private var currentDropPosition: Int? = null
    private var dropJob: Job? = null

    fun startDrag(offset: Offset): Boolean {
        val draggedItem = listState.layoutInfo.visibleItemsInfo.find {
            Rect(
                (it.offset + listState.layoutInfo.beforeContentPadding).toOffset(),
                it.size.toSize()
            ).contains(offset)
        } ?: return false

        if (!onDragStart(draggedItem)) return false
        this.draggedItem = draggedItem
        draggedItemAbsolutePosition = draggedItem.offset.toOffset()
        return true
    }

    suspend fun drag(dragAmount: Offset) {
        val absPosition = draggedItemAbsolutePosition
        val draggedItem = draggedItem
        if (absPosition != null && draggedItem != null) {
            draggedItemAbsolutePosition = absPosition + dragAmount
            val draggedCenter = Rect(absPosition, draggedItem.size.toSize()).center
            val dragOver = listState.layoutInfo.visibleItemsInfo.find {
                Rect(
                    it.offset.toOffset(),
                    it.size.toSize()
                ).contains(draggedCenter)
            }

            if (dragOver != null && dragOver.key != draggedItem.key) {
                attemptMove(dragOver)
            }

            val toStart =
                if (listState.layoutInfo.orientation == Orientation.Horizontal) draggedCenter.x else draggedCenter.y


            if (toStart - listState.layoutInfo.viewportStartOffset < scrollEdgeSize) {
                enableScrolling(-scrollDelta)
            } else if (listState.layoutInfo.viewportEndOffset - toStart < scrollEdgeSize) {
                enableScrolling(scrollDelta)
            } else {
                endScrolling()
            }

            draggedItemOffset?.let { onDrag(draggedItem, it) }
        }
    }

    private fun Int.toOffset(): Offset {
        return if (listState.layoutInfo.orientation == Orientation.Horizontal) {
            Offset(this.toFloat(), 0f)
        } else {
            Offset(0f, this.toFloat())
        }
    }

    private fun Int.toSize(): Size {
        return if (listState.layoutInfo.orientation == Orientation.Horizontal) {
            Size(this.toFloat(), listState.layoutInfo.viewportSize.height.toFloat())
        } else {
            Size(listState.layoutInfo.viewportSize.width.toFloat(), this.toFloat())
        }
    }

    /**
     * Move the currently dragged item to the specified drop target if the dragged item is held
     * for at least 300ms over the drop target. The move operation is canceled if the dragged item
     * is released or moved out of the dropTarget area during the 300ms time frame.
     */
    private suspend fun attemptMove(dropTarget: LazyListItemInfo) {
        if (currentDropPosition != dropTarget.index) {
            coroutineScope {
                dropJob?.cancelAndJoin()
                dropJob = launch {
                    currentDropPosition = dropTarget.index
                    delay(300)
                    // Get a fresh copy of layout info because index in saved layout info might be outdated
                    val dragged =
                        listState.layoutInfo.visibleItemsInfo.find { it.key == draggedItem?.key }
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
                    listState.scrollBy(delta * timeDelta / 1000_000_000f)
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
fun LazyDragAndDropRow(
    state: LazyDragAndDropListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    //reverseLayout: Boolean = false, //TODO: Fix reverse layout
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    bidirectionalDrag: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        modifier = modifier.dragAndDrop(
            state,
            isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
            hapticFeedback = LocalHapticFeedback.current,
            dragVertical = bidirectionalDrag,
            dragHorizontal = true,
        ),
        state = state.listState,
        contentPadding = contentPadding,
        reverseLayout = false,
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

@Composable
fun LazyDragAndDropColumn(
    state: LazyDragAndDropListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    //reverseLayout: Boolean = false, //TODO: Fix reverse layout
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    bidirectionalDrag: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier.dragAndDrop(
            state,
            isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
            hapticFeedback = LocalHapticFeedback.current,
            dragVertical = true,
            dragHorizontal = bidirectionalDrag,
        ),
        state = state.listState,
        contentPadding = contentPadding,
        reverseLayout = false,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

fun Modifier.dragAndDrop(
    state: LazyDragAndDropListState,
    isRtl: Boolean,
    dragVertical: Boolean = true,
    dragHorizontal: Boolean = true,
    hapticFeedback: HapticFeedback
) =
    this then Modifier.pointerInput(null) {
        val scope = CoroutineScope(coroutineContext)
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                if (state.startDrag(offset)) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { _, dragAmount ->
                scope.launch {
                    state.drag(
                        when {
                            !dragVertical && !dragHorizontal -> Offset.Zero
                            dragVertical && !dragHorizontal -> Offset(0f, dragAmount.y)
                            !dragVertical && dragHorizontal && isRtl -> Offset(-dragAmount.x, 0f)
                            !dragVertical && dragHorizontal && !isRtl -> Offset(dragAmount.x, 0f)
                            dragVertical && dragHorizontal && isRtl -> Offset(-dragAmount.x, dragAmount.y)
                            else -> dragAmount
                        }
                    )
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
fun LazyItemScope.DraggableItem(
    modifier: Modifier = Modifier,
    state: LazyDragAndDropListState,
    key: Any?,
    content: @Composable BoxScope.(isDragged: Boolean) -> Unit
) {
    val isDragged = state.draggedItem?.key == key || state.droppedItemKey == key
    Box(
        modifier = modifier
            .then(if (isDragged) Modifier else Modifier.animateItem())
            .zIndex(if (isDragged) 1f else 0f)
            .offset {
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
