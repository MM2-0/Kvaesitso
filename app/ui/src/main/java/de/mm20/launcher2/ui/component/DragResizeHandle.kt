package de.mm20.launcher2.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.min

enum class ResizeAxis {
    Horizontal,
    Vertical,
    Both,
    None,
}

@Composable
fun DragResizeHandle(
    resizeAxis: ResizeAxis = ResizeAxis.Both,
    alignment: Alignment = Alignment.TopStart,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 0.dp,
    maxWidth: Dp = Dp.Infinity,
    maxHeight: Dp = Dp.Infinity,
    snapToMeasuredWidth: Boolean = false,
    snapToMeasuredHeight: Boolean = false,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    onResize: (width: Dp, height: Dp) -> Unit,
    onResizeStopped: () -> Unit = { },
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val measuredWidth = this.maxWidth
        val measuredHeight = this.maxHeight

        val actualMaxWidth = if (maxWidth == Dp.Unspecified) measuredWidth else min(measuredWidth, maxWidth)
        val actualMaxHeight = if (maxHeight == Dp.Unspecified) measuredHeight else min(measuredHeight, maxHeight)

        var dragging by remember { mutableStateOf(false) }

        val density = LocalDensity.current

        val hapticFeedback = LocalHapticFeedback.current

        Box(
            modifier = Modifier
                .then(if (width.isUnspecified) Modifier.fillMaxWidth() else Modifier.width(width))
                .then(if (height.isUnspecified) Modifier.fillMaxHeight() else Modifier.height(height))
                .align(alignment)
                .border(1.dp, color = MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        ) {
            if (resizeAxis == ResizeAxis.Both || resizeAxis == ResizeAxis.Horizontal) {
                val horizontalDragState = rememberDraggableState {
                    val currentWidth = if (width.isUnspecified) measuredWidth else width
                    val dragDelta =
                        when (alignment) {
                            Alignment.Center, Alignment.TopCenter, Alignment.BottomCenter -> it * 2
                            else -> it
                        }

                    val newWidth = (currentWidth + with(density) { dragDelta.toDp() }).coerceIn(
                        minWidth,
                        actualMaxWidth
                    )

                    if (snapToMeasuredWidth &&
                        newWidth > actualMaxWidth - 16.dp &&
                        width < actualMaxWidth &&
                        dragDelta > 0
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResize(actualMaxWidth, height)
                    } else if (
                        snapToMeasuredWidth &&
                        newWidth <= minWidth + 16.dp &&
                        width > minWidth &&
                        dragDelta < 0
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResize(minWidth, height)
                    } else {
                        onResize(newWidth, height)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = min(128.dp, width) / 2)
                        .draggable(
                            state = horizontalDragState,
                            orientation = Orientation.Horizontal,
                            onDragStarted = {
                                dragging = true
                            },
                            onDragStopped = {
                                onResizeStopped()
                                dragging = false
                            },
                            startDragImmediately = true,
                        )
                        .requiredSize(width = min(128.dp, width), height = height)
                ) {
                    Icon(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp)
                            .rotate(90f)
                            .align(Alignment.Center),
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            if (resizeAxis == ResizeAxis.Both || resizeAxis == ResizeAxis.Vertical) {
                val verticalDragState = rememberDraggableState {
                    val currentHeight = if (height.isUnspecified) measuredHeight else height
                    val dragDelta =
                        when (alignment) {
                            Alignment.Center, Alignment.CenterStart, Alignment.CenterEnd -> it * 2
                            else -> it
                        }
                    val newHeight = (currentHeight + with(density) { dragDelta.toDp() }).coerceIn(
                        minHeight,
                        actualMaxHeight
                    )

                    if (snapToMeasuredHeight &&
                        newHeight > actualMaxHeight - 16.dp &&
                        height < actualMaxHeight &&
                        dragDelta > 0
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResize(width, actualMaxHeight)
                    } else if (
                        snapToMeasuredHeight &&
                        newHeight <= minHeight + 16.dp &&
                        height > minHeight &&
                        dragDelta < 0
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResize(width, minHeight)
                    } else {
                        onResize(width, newHeight)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = min(64.dp, height) / 2)
                        .draggable(
                            state = verticalDragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                dragging = true
                            },
                            onDragStopped = {
                                onResizeStopped()
                                dragging = false
                            },
                            startDragImmediately = true,
                        )
                        .requiredSize(height = min(64.dp, height), width = width)
                ) {
                    Icon(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp)
                            .align(Alignment.Center),
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            AnimatedVisibility(
                visible = dragging,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Text(
                        "W: ${formatDimension(width)} H: ${formatDimension(height)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private fun formatDimension(value: Dp): String {
    if (value.isUnspecified) {
        return "100%"
    }
    return "${value.value.toInt()}dp"
}