package de.mm20.launcher2.ui.component

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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

        val density = LocalDensity.current

        val hapticFeedback = LocalHapticFeedback.current

        Box(
            modifier = Modifier
                .then(if (width.isUnspecified) Modifier.fillMaxWidth() else Modifier.width(width))
                .then(if (height.isUnspecified) Modifier.fillMaxHeight() else Modifier.height(height))
                .align(alignment)
                .border(1.dp, color = MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
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
                        maxWidth
                    )

                    if (snapToMeasuredWidth &&
                        maxWidth >= measuredWidth &&
                        newWidth > measuredWidth - 16.dp &&
                        dragDelta > 0
                    ) {
                        if (!width.isUnspecified) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResize(Dp.Unspecified, height)
                        }
                    } else {
                        onResize(newWidth, height)
                    }

                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 64.dp)
                        .draggable(
                            state = horizontalDragState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                onResizeStopped()
                            },
                            startDragImmediately = true,
                        )
                        .requiredSize(128.dp)
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
                        maxHeight
                    )

                    if (snapToMeasuredHeight &&
                        maxHeight >= measuredHeight &&
                        newHeight > measuredHeight - 16.dp &&
                        dragDelta > 0
                    ) {
                        if (!height.isUnspecified) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResize(width, Dp.Unspecified)
                        }
                    } else {
                        onResize(width, newHeight)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 64.dp)
                        .draggable(
                            state = verticalDragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                onResizeStopped()
                            },
                            startDragImmediately = true,
                        )
                        .requiredSize(128.dp)
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
        }
    }
}