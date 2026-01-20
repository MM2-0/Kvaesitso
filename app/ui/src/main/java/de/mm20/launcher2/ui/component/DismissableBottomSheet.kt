package de.mm20.launcher2.ui.component

import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.overlays.Overlay
import kotlinx.coroutines.CancellationException

@Composable
fun DismissableBottomSheet(
    expanded: Boolean = true,
    onDismissRequest: () -> Unit,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    DismissableBottomSheet(
        state = expanded,
        expanded = { it },
        onDismissRequest = onDismissRequest,
    ) {
        content(PaddingValues())
    }
}

/**
 * A bottom sheet that can be dismissed by swiping down.
 *
 * Use `BottomSheet` for a sheet that can't be dismissed.
 *
 * @param expanded Whether the bottom sheet is visible.
 * @param onDismissRequest Called when the bottom sheet is dismissed.
 * @param content The content of the bottom sheet.
 */
@Composable
fun <T> DismissableBottomSheet(
    state: T,
    expanded: (T) -> Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable (state: T) -> Unit,
) {

    val expandedState = remember { MutableTransitionState(state) }
    expandedState.targetState = state

    val expandedCurrent = expanded(expandedState.currentState)
    val expandedTarget = expanded(expandedState.targetState)

    if (expandedCurrent || expandedTarget) {

        Overlay {
            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) {
                focusManager.clearFocus(true)
            }

            val backProgress = remember { Animatable(0f) }

            val draggableState = remember {
                AnchoredDraggableState(SheetValue.Hidden, DraggableAnchors {
                    SheetValue.Hidden at 0f
                })
            }

            PredictiveBackHandler {
                try {
                    it.collect {
                        backProgress.snapTo(it.progress)
                    }
                    onDismissRequest()
                } catch (_: CancellationException) {
                    backProgress.animateTo(1f)
                }

            }

            val motionSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

            val transition = rememberTransition(expandedState)

            val scrimColor by transition.animateColor(
                transitionSpec = { MaterialTheme.motionScheme.slowEffectsSpec() },
            ) { if (expanded(it)) BottomSheetDefaults.ScrimColor else Color.Transparent }


            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .graphicsLayer {
                        scaleX = 1f - backProgress.value * 0.1f
                        scaleY = 1f - backProgress.value * 0.1f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .imePadding()
            ) {

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onDismissRequest()
                            }
                        }
                )

                val focusRequester = remember { FocusRequester() }

                if (draggableState.anchors.size > 1) {
                    LaunchedEffect(Unit) {
                        draggableState.animateTo(SheetValue.PartiallyExpanded, motionSpec)
                    }

                    if (draggableState.settledValue != SheetValue.Hidden) {
                        DisposableEffect(Unit) {
                            focusRequester.requestFocus()
                            onDispose {
                                focusRequester.freeFocus()
                            }
                        }
                    }

                    LaunchedEffect(expandedTarget) {
                        if (expandedTarget != expandedCurrent && draggableState.settledValue == draggableState.targetValue) {
                            if (!expandedTarget && draggableState.settledValue != SheetValue.Hidden) {
                                draggableState.animateTo(SheetValue.Hidden, motionSpec)
                            }
                        }
                    }

                    LaunchedEffect(draggableState.settledValue) {
                        if (draggableState.settledValue == draggableState.targetValue) {
                            if (draggableState.settledValue == SheetValue.Hidden) {
                                onDismissRequest()
                            }
                        }
                    }
                }


                val containerHeight = maxHeight.toPixels()
                var sheetHeight by remember { mutableIntStateOf(0) }

                val flingBehavior =
                    AnchoredDraggableDefaults.flingBehavior(
                        state = draggableState,
                        animationSpec = motionSpec,
                    )

                Surface(
                    shadowElevation = 1.dp,
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .statusBarsPadding()
                        .focusRequester(focusRequester)
                        .nestedScroll(
                            remember(draggableState) {
                                BottomSheetNestedScrollConnection(
                                    anchoredDraggableState = draggableState,
                                    flingBehavior = flingBehavior,
                                )
                            }
                        )
                        .onSizeChanged {
                            sheetHeight = it.height
                            draggableState.updateAnchors(
                                DraggableAnchors {
                                    SheetValue.Hidden at 0f
                                    if (it.height > containerHeight * 0.5f) {
                                        SheetValue.PartiallyExpanded at containerHeight * -0.5f
                                        SheetValue.Expanded at it.height * -1f
                                    } else {
                                        SheetValue.PartiallyExpanded at it.height * -1f
                                    }
                                },
                            )
                        }
                        .fillMaxWidth()
                        .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                        .align(Alignment.BottomCenter)
                        .wrapContentHeight()
                        .heightIn(max = maxHeight)
                        .graphicsLayer {
                            translationY = draggableState.offset.coerceAtMost(0f) + sheetHeight
                        }
                        .anchoredDraggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            flingBehavior = flingBehavior,
                        )
                ) {
                    CompositionLocalProvider(
                        LocalOverscrollFactory provides null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .consumeWindowInsets(WindowInsets.ime),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BottomSheetDefaults.DragHandle()
                            Box {
                                content(
                                    if (expandedTarget) expandedState.targetState else expandedState.currentState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun BottomSheetNestedScrollConnection(
    anchoredDraggableState: AnchoredDraggableState<SheetValue>,
    flingBehavior: FlingBehavior,
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            return if (delta < 0 && source == NestedScrollSource.UserInput) {
                anchoredDraggableState.dispatchRawDelta(delta).toOffset()
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            return if (source == NestedScrollSource.UserInput) {
                anchoredDraggableState.dispatchRawDelta(available.y).toOffset()
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val toFling = available.y
            val currentOffset = anchoredDraggableState.requireOffset()
            val minAnchor = anchoredDraggableState.anchors.minPosition()
            return if (toFling < 0 && currentOffset > minAnchor) {
                anchoredDraggableState.anchoredDrag(flingBehavior, toFling)
                // since we go to the anchor with tween settling, consume all for the best UX
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val toFling = available.y
            val consumedByAnchoredDraggableFling =
                anchoredDraggableState.anchoredDrag(flingBehavior, toFling)
            return Velocity(consumed.x, consumedByAnchoredDraggableFling)
        }

        private fun Float.toOffset(): Offset =
            Offset(x = 0f, y = this)

        private fun AnchoredDraggableState<SheetValue>.newOffsetForDelta(delta: Float) =
            ((if (offset.isNaN()) 0f else offset) + delta).coerceIn(
                anchoredDraggableState.anchors.minPosition(),
                anchoredDraggableState.anchors.maxPosition(),
            )

        private suspend fun AnchoredDraggableState<SheetValue>.anchoredDrag(
            flingBehavior: FlingBehavior,
            initialVelocity: Float
        ): Float {
            var consumedVelocity = 0f
            anchoredDraggableState.anchoredDrag {
                val scrollScope =
                    object : ScrollScope {
                        override fun scrollBy(pixels: Float): Float {
                            val newOffset = newOffsetForDelta(pixels)
                            val consumed = newOffset - offset
                            dragTo(newOffset)
                            return consumed
                        }
                    }
                consumedVelocity = with(flingBehavior) { scrollScope.performFling(initialVelocity) }
            }
            return consumedVelocity
        }
    }