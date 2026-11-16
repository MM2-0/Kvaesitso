/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.relays.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex

@Stable
class RelayDragState(
    val onMove: (from: Int, to: Int) -> Unit,
    val itemCount: () -> Int,
) {
    var draggedItemIndex by mutableIntStateOf(-1)
    var dragOffset by mutableFloatStateOf(0f)
    val itemHeights = mutableStateMapOf<Int, Float>()
    val isDragging: Boolean get() = draggedItemIndex >= 0

    fun onDragStart(index: Int) {
        draggedItemIndex = index
        dragOffset = 0f
    }

    fun onDrag(dragAmount: Float) {
        dragOffset += dragAmount

        val currentIndex = draggedItemIndex
        if (currentIndex < 0) return

        // Check if we should swap with the item above
        if (dragOffset < 0 && currentIndex > 0) {
            val aboveHeight = itemHeights[currentIndex - 1] ?: 0f
            if (-dragOffset > aboveHeight / 2f) {
                onMove(currentIndex, currentIndex - 1)

                val h1 = itemHeights[currentIndex]
                val h2 = itemHeights[currentIndex - 1]
                if (h1 != null) itemHeights[currentIndex - 1] = h1
                if (h2 != null) itemHeights[currentIndex] = h2

                dragOffset += aboveHeight
                draggedItemIndex = currentIndex - 1
                return
            }
        }

        // Check if we should swap with the item below
        if (dragOffset > 0 && currentIndex < itemCount() - 1) {
            val belowHeight = itemHeights[currentIndex + 1] ?: 0f
            if (dragOffset > belowHeight / 2f) {
                onMove(currentIndex, currentIndex + 1)

                val h1 = itemHeights[currentIndex]
                val h2 = itemHeights[currentIndex + 1]
                if (h1 != null) itemHeights[currentIndex + 1] = h1
                if (h2 != null) itemHeights[currentIndex] = h2

                dragOffset -= belowHeight
                draggedItemIndex = currentIndex + 1
                return
            }
        }
    }

    fun onDragEnd() {
        draggedItemIndex = -1
        dragOffset = 0f
    }
}

@Composable
fun rememberRelayDragState(
    onMove: (from: Int, to: Int) -> Unit,
    itemCount: () -> Int,
): RelayDragState =
    remember(onMove, itemCount) {
        RelayDragState(onMove, itemCount)
    }

@Composable
fun Modifier.draggableRelayItem(
    index: Int,
    dragState: RelayDragState,
): Modifier {
    val isDragging = dragState.draggedItemIndex == index
    val targetElevation = if (isDragging) 8f else 0f
    val animatedElevation by animateFloatAsState(
        targetValue = targetElevation,
        label = "dragElevation",
    )

    return this
        .zIndex(if (isDragging) 1f else 0f)
        .onGloballyPositioned { coordinates ->
            dragState.itemHeights[index] = coordinates.size.height.toFloat()
        }.graphicsLayer {
            translationY = if (isDragging) dragState.dragOffset else 0f
            shadowElevation = animatedElevation
            if (isDragging) {
                scaleX = 1.02f
                scaleY = 1.02f
            }
        }
}

@Composable
fun Modifier.relayDragHandle(
    index: Int,
    dragState: RelayDragState,
): Modifier {
    // rememberUpdatedState keeps the index current without restarting
    // the pointerInput scope (which would cancel the in-progress gesture)
    val currentIndex by rememberUpdatedState(index)
    return this.pointerInput(dragState) {
        detectDragGestures(
            onDragStart = { dragState.onDragStart(currentIndex) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragState.onDragEnd() },
            onDragCancel = { dragState.onDragEnd() },
        )
    }
}
