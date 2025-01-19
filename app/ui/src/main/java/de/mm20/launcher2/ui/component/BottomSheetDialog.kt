package de.mm20.launcher2.ui.component

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.gestures.LauncherGestureHandler
import de.mm20.launcher2.ui.overlays.LocalZIndex
import de.mm20.launcher2.ui.overlays.Overlay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable (paddingValues: PaddingValues) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest,
        modifier = Modifier.padding()
    ) {
        content(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
        if (confirmButton != null || dismissButton != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 24.dp, vertical = 8.dp)),
                horizontalArrangement = Arrangement.End
            ) {
                if (dismissButton != null) {
                    dismissButton()
                }
                if (confirmButton != null && dismissButton != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (confirmButton != null) {
                    confirmButton()
                }
            }
        }
    }
    /*CompositionLocalProvider(
        LocalAbsoluteTonalElevation provides 0.dp,
    ) {
        Overlay(zIndex = zIndex) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                propagateMinConstraints = true,
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxHeight = maxHeight
                val scrimAlpha by animateFloatAsState(
                    if (draggableState.targetValue == SwipeState.Dismiss) 0f else 0.32f,
                    label = "Scrim alpha"
                )

                Box(modifier = Modifier
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                    .fillMaxSize()
                    .pointerInput(onDismissRequest, dismissible) {
                        detectTapGestures {
                            if (dismissible()) {
                                scope.launch {
                                    draggableState.animateTo(SwipeState.Dismiss)
                                    onDismissRequest()
                                }
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    var sheetHeight by remember {
                        mutableStateOf(0f)
                    }

                    val maxHeightPx = maxHeight.toPixels()
                    LaunchedEffect(maxHeightPx, sheetHeight) {
                        val oldValue = draggableState.currentValue
                        val hasPeekAnchor = sheetHeight > 0f
                        val hasFullAnchor = sheetHeight > maxHeightPx * 0.5f
                        // If the sheet was hidden, move it to peek. Otherwise, try to keep the previous state, if possible.
                        val newValue = when {
                            oldValue == SwipeState.Dismiss && hasPeekAnchor -> SwipeState.Peek
                            oldValue == SwipeState.Peek && hasPeekAnchor -> SwipeState.Peek
                            oldValue == SwipeState.Full && hasFullAnchor -> SwipeState.Full
                            oldValue == SwipeState.Full && hasPeekAnchor -> SwipeState.Peek
                            else -> SwipeState.Dismiss
                        }
                        val newAnchors = DraggableAnchors {
                            SwipeState.Dismiss at 0f
                            if (hasPeekAnchor) SwipeState.Peek at -min(
                                maxHeightPx * 0.5f,
                                sheetHeight
                            )
                            if (hasFullAnchor) SwipeState.Full at -min(maxHeightPx, sheetHeight)
                        }
                        draggableState.updateAnchors(
                            newAnchors,
                            with(draggableState) {
                                (if (!offset.isNaN()) {
                                    newAnchors.closestAnchor(offset) ?: targetValue
                                } else targetValue).let {
                                    if (it == SwipeState.Dismiss && targetValue != SwipeState.Dismiss && hasPeekAnchor) SwipeState.Peek else it
                                }
                            },
                        )
                        if (newValue != oldValue) {
                            draggableState.animateTo(newValue)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .nestedScroll(nestedScrollConnection)
                            .onSizeChanged {
                                sheetHeight = it.height.toFloat()
                            }
                            .offset {
                                IntOffset(0,
                                    maxHeightPx.toInt() +
                                            (draggableState.offset
                                                .takeIf { !it.isNaN() }
                                                ?.roundToInt() ?: 0)
                                )
                            }
                            .anchoredDraggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                            )
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge.copy(
                            bottomStart = CornerSize(0),
                            bottomEnd = CornerSize(0),
                        ),
                        shadowElevation = 16.dp,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .systemBarsPadding()
                        ) {
                            Column {
                                if (title != null || actions != null) {
                                    CenterAlignedTopAppBar(
                                        title = title ?: { BottomSheetDefaults.DragHandle() },
                                        actions = actions ?: {},
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = Color.Transparent,
                                        ),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        BottomSheetDefaults.DragHandle()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (confirmButton != null || dismissButton != null) Modifier.padding(
                                                bottom = 64.dp
                                            ) else Modifier
                                        )
                                        .wrapContentHeight(),
                                    propagateMinConstraints = true,
                                    contentAlignment = Alignment.Center
                                ) {
                                    content(PaddingValues(horizontal = 24.dp, vertical = 8.dp))
                                }



                            }
                        }
                    }

                }
            }
        }
    }*/
}

private enum class SwipeState {
    Full, Peek, Dismiss
}

private class BottomSheetPositionProvider(val insets: WindowInsets, val density: Density) :
    PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        return IntOffset.Zero + IntOffset(
            insets.getLeft(density, layoutDirection),
            insets.getTop(density)
        )
    }
}