package de.mm20.launcher2.ui.launcher.scaffold

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.ktx.toPixels
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

sealed interface ScaffoldAction {
    data object Search : ScaffoldAction
    data object Widgets : ScaffoldAction
    data class Shortcut(val searchable: SavableSearchable) : ScaffoldAction
    data object ScreenOff : ScaffoldAction
    data object Notifications : ScaffoldAction
    data object QuickSettings : ScaffoldAction
    data object Recents : ScaffoldAction
}

enum class ScaffoldAnimation {
    Rubberband,
    Push,
}

data class ScaffoldGesture(
    val component: ScaffoldComponent,
    val animation: ScaffoldAnimation,
)

enum class SearchBarPosition {
    Top,
    Bottom,
}

data class ScaffoldConfiguration(
    val homeComponent: ScaffoldComponent,
    val swipeUp: ScaffoldGesture? = null,
    val swipeDown: ScaffoldGesture? = null,
    val swipeLeft: ScaffoldGesture? = null,
    val swipeRight: ScaffoldGesture? = null,
    val doubleTap: ScaffoldGesture? = null,
    val longPress: ScaffoldGesture? = null,
    val searchBarPosition: SearchBarPosition = SearchBarPosition.Top,
)

private operator fun ScaffoldConfiguration.get(direction: SwipeDirection): ScaffoldGesture? {
    return when (direction) {
        SwipeDirection.Up -> swipeUp
        SwipeDirection.Down -> swipeDown
        SwipeDirection.Left -> swipeLeft
        SwipeDirection.Right -> swipeRight
    }
}

enum class SwipeDirection(val orientation: Orientation) {
    Up(Orientation.Vertical),
    Down(Orientation.Vertical),
    Left(Orientation.Horizontal),
    Right(Orientation.Horizontal),
}

private val ScaffoldGesture?.maxDragFactor: Float
    get() {
        if (this == null) return 0f
        if (this.animation == ScaffoldAnimation.Rubberband) return 2f
        return 1f
    }

private class LauncherScaffoldState(
    private val config: ScaffoldConfiguration,
    val size: Size,
    private val touchSlop: Float,
    private val rubberbandThreshold: Float,
    private val minFlingVelocity: Float,
) {
    var currentOffset by mutableStateOf(Offset.Zero)
        private set
    var currentDragDirection by mutableStateOf<SwipeDirection?>(null)
        private set

    /**
     * True if any page is open, false if on home page.
     */
    private var isPageOpen = false

    /**
     * 0..1 current progress
     * 0: home page
     * 1: any other page
     */
    val currentProgress by derivedStateOf {
        val dir = currentDragDirection ?: return@derivedStateOf 0f
        val gesture = config[dir] ?: return@derivedStateOf 0f

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            val offset =
                (currentOffset.x + currentOffset.y).absoluteValue.coerceAtMost(rubberbandThreshold)
            if (isPageOpen) {
                1f - offset / (rubberbandThreshold * 2f)
            } else {
                offset / (rubberbandThreshold * 2f)
            }
        } else {
            if (dir.orientation == Orientation.Horizontal) {
                (currentOffset.x.absoluteValue / size.width).coerceIn(0f, 1f)
            } else {
                (currentOffset.y.absoluteValue / size.height).coerceIn(0f, 1f)
            }
        }
    }

    val currentAnimation by derivedStateOf {
        val dir = currentDragDirection ?: return@derivedStateOf null
        config[dir]?.animation
    }

    private val animatable =
        Animatable<Offset, AnimationVector2D>(Offset.Zero, Offset.VectorConverter)

    suspend fun onDragStarted() {
        animatable.stop()
    }

    fun onDrag(offset: Offset) {
        if (currentDragDirection == null || (!isPageOpen && currentOffset.x.absoluteValue <= touchSlop && currentOffset.y.absoluteValue <= touchSlop)) {
            currentDragDirection = getSwipeDirection(config, offset)
        }

        val direction = currentDragDirection ?: return

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandDrag(direction, currentOffset, offset)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushDrag(direction, currentOffset, offset)
        }


    }

    private fun performRubberbandDrag(direction: SwipeDirection, offset: Offset, delta: Offset) {
        val threshold = rubberbandThreshold * 1.5f
        currentOffset = when (direction) {
            SwipeDirection.Up -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-threshold, threshold)
            )

            SwipeDirection.Down -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-threshold, threshold)
            )

            SwipeDirection.Left -> Offset(
                (offset.x + delta.x).coerceIn(-threshold, threshold),
                0f
            )

            SwipeDirection.Right -> Offset(
                (offset.x + delta.x).coerceIn(-threshold, threshold),
                0f
            )
        }
    }

    /**
     * @param direction The direction of the drag (currentDragDirection)
     * @param offset The total offset of the drag (currentOffset)
     * @param delta The delta of the drag (offset)
     */
    private fun performPushDrag(direction: SwipeDirection, offset: Offset, delta: Offset) {
        currentOffset = when (direction) {
            SwipeDirection.Up -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(-size.height, 0f)
            )

            SwipeDirection.Down -> Offset(
                0f,
                (offset.y + delta.y).coerceIn(0f, size.height)
            )

            SwipeDirection.Left -> Offset(
                (offset.x + delta.x).coerceIn(-size.width, 0f),
                0f
            )

            SwipeDirection.Right -> Offset(
                (offset.x + delta.x).coerceIn(0f, size.width),
                0f
            )

        }
    }

    private fun getSwipeDirection(config: ScaffoldConfiguration, offset: Offset): SwipeDirection? {
        when {
            (offset.x > 0 && offset.y > 0) -> {
                return if (offset.x > offset.y && config.swipeRight != null) {
                    SwipeDirection.Right
                } else if (config.swipeDown != null) {
                    SwipeDirection.Down
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y < 0) -> {
                return if (offset.x < offset.y && config.swipeLeft != null) {
                    SwipeDirection.Left
                } else if (config.swipeUp != null) {
                    SwipeDirection.Up
                } else {
                    null
                }
            }

            (offset.x > 0 && offset.y < 0) -> {
                return if (offset.x > -offset.y && config.swipeRight != null) {
                    SwipeDirection.Right
                } else if (config.swipeUp != null) {
                    SwipeDirection.Up
                } else {
                    null
                }
            }

            (offset.x < 0 && offset.y > 0) -> {
                return if (offset.x < -offset.y && config.swipeLeft != null) {
                    SwipeDirection.Left
                } else if (config.swipeDown != null) {
                    SwipeDirection.Down
                } else {
                    null
                }
            }
        }
        return null
    }

    suspend fun onDragStopped(velocity: Velocity) {
        if (currentDragDirection == null) {
            currentOffset = Offset.Zero
        }
        val direction = currentDragDirection ?: return
        val offset = currentOffset
        val wasPageOpen = isPageOpen

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandFling(direction, offset, velocity)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushFling(direction, offset, velocity)
        }

        if (isPageOpen != wasPageOpen) {
            if (wasPageOpen) {
                config.homeComponent.onMount()
                gesture.component.onUnmount()
            } else {
                gesture.component.onMount()
                config.homeComponent.onUnmount()
            }
        }

        if (!gesture.component.permanent) {
            isPageOpen = false
            currentOffset = Offset.Zero
        }
        if (!isPageOpen) currentDragDirection = null
    }

    private suspend fun performRubberbandFling(
        direction: SwipeDirection,
        offset: Offset,
        velocity: Velocity
    ) {

        if (offset.x <= -rubberbandThreshold || offset.x < 0f && velocity.x < -minFlingVelocity) {
            isPageOpen = !isPageOpen
        } else if (offset.x >= rubberbandThreshold || offset.x > 0f && velocity.x > minFlingVelocity) {
            isPageOpen = !isPageOpen
        } else if (offset.y <= -rubberbandThreshold || offset.y < 0f && velocity.y < -minFlingVelocity) {
            isPageOpen = !isPageOpen
        } else if (offset.y >= rubberbandThreshold || offset.y > 0f && velocity.y > minFlingVelocity) {
            isPageOpen = !isPageOpen
        }

        animatable.snapTo(currentOffset)
        animatable.animateTo(
            Offset.Zero,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }

    private suspend fun performPushFling(
        direction: SwipeDirection,
        offset: Offset,
        velocity: Velocity
    ) {
        val lowerPage = when (direction) {
            SwipeDirection.Up -> -size.height
            SwipeDirection.Down -> 0f
            SwipeDirection.Left -> -size.width
            SwipeDirection.Right -> 0f
        }

        val upperPage = if (direction.orientation == Orientation.Vertical) {
            lowerPage + size.height
        } else {
            lowerPage + size.width
        }

        Log.d("LauncherScaffold", "performPushFling: $velocity, $minFlingVelocity")

        val threshold = (upperPage + lowerPage) / 2f

        val targetOffset = if (direction.orientation == Orientation.Vertical) {
            if (offset.y > threshold && velocity.y > -minFlingVelocity || velocity.y > minFlingVelocity) Offset(
                0f,
                upperPage
            )
            else Offset(0f, lowerPage)
        } else {
            if (offset.x > threshold && velocity.x > -minFlingVelocity || velocity.x > minFlingVelocity) Offset(
                upperPage,
                0f
            )
            else Offset(lowerPage, 0f)
        }

        isPageOpen = targetOffset != Offset.Zero

        animatable.snapTo(currentOffset)
        animatable.animateTo(
            targetOffset,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }
}

@Composable
fun LauncherScaffold(
    config: ScaffoldConfiguration = remember {
        ScaffoldConfiguration(
            homeComponent = ClockWidgetComponent(),
            swipeDown = ScaffoldGesture(
                component = SearchComponent(),
                animation = ScaffoldAnimation.Rubberband,
            ),
            swipeLeft = ScaffoldGesture(
                component = ClockWidgetComponent(),
                animation = ScaffoldAnimation.Push,
            ),
            swipeRight = ScaffoldGesture(
                component = WidgetsComponent(),
                animation = ScaffoldAnimation.Push,
            ),
            swipeUp = ScaffoldGesture(
                component = WidgetsComponent(),
                animation = ScaffoldAnimation.Push,
            )
        )
    }
) {

    BoxWithConstraints {
        val width = this.maxWidth
        val height = this.maxHeight
        val widthPx = width.toPixels()
        val heightPx = height.toPixels()

        val touchSlop = LocalViewConfiguration.current.touchSlop
        val minFlingVelocity = 125.dp.toPixels()
        val rubberbandThreshold = 64.dp.toPixels()

        val state =
            remember(widthPx, heightPx, touchSlop, rubberbandThreshold, minFlingVelocity, config) {
                LauncherScaffoldState(
                    config = config,
                    size = Size(widthPx, heightPx),
                    touchSlop = touchSlop,
                    rubberbandThreshold = rubberbandThreshold,
                    minFlingVelocity = minFlingVelocity,
                )
            }

        val hapticFeedback = LocalHapticFeedback.current
        LaunchedEffect(state.currentProgress >= 0.5f, state.currentProgress <= 0.5f) {
            if (state.currentProgress >= 0f &&  state.currentProgress <= 1f) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }

        val scope = rememberCoroutineScope()

        val draggableState = rememberDraggable2DState {
            state.onDrag(it)
        }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero

                    if (state.currentProgress != 0f && state.currentProgress != 1f) {
                        draggableState.dispatchRawDelta(available)
                        return available
                    }

                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        draggableState.dispatchRawDelta(available)
                        return available
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity
                ): Velocity {
                    state.onDragStopped(available / 25f)
                    return available
                }
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .draggable2D(
                    state = draggableState,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.onDragStopped(velocity)
                        }
                    }
                )
        ) {
            config.homeComponent.content(
                Modifier
                    .fillMaxSize()
                    .homePageAnimation(state),
                WindowInsets.systemBars.asPaddingValues(),
                1f - state.currentProgress,
            )

            SecondaryPage(
                state = state,
                modifier = Modifier
                    .fillMaxSize(),
                swipeDownContent = config.swipeDown?.component?.content,
                swipeLeftContent = config.swipeLeft?.component?.content,
                swipeRightContent = config.swipeRight?.component?.content,
                swipeUpContent = config.swipeUp?.component?.content,
            )
        }
    }
}

@Composable
private fun SecondaryPage(
    state: LauncherScaffoldState,
    modifier: Modifier = Modifier,
    insets: PaddingValues = WindowInsets.systemBars.asPaddingValues(),
    swipeDownContent: ComponentContent?,
    swipeLeftContent: ComponentContent?,
    swipeRightContent: ComponentContent?,
    swipeUpContent: ComponentContent?,
) {
    val rememberedDownContent = remember(swipeDownContent) {
        if (swipeDownContent != null) movableContentOf(swipeDownContent)
        else null
    }
    val rememberedLeftContent = remember(swipeLeftContent) {
        if (swipeLeftContent != null) movableContentOf(swipeLeftContent)
        else null
    }
    val rememberedRightContent = remember(swipeRightContent) {
        if (swipeRightContent != null) movableContentOf(swipeRightContent)
        else null
    }
    val rememberedUpContent = remember(swipeUpContent) {
        if (swipeUpContent != null) movableContentOf(swipeUpContent)
        else null
    }

    val dir = state.currentDragDirection
    val prg = state.currentProgress

    if (dir != null) {
        val mod = modifier.secondaryPageAnimation(state)

        when (dir) {
            SwipeDirection.Up -> rememberedUpContent?.invoke(mod, insets, prg)
            SwipeDirection.Down -> rememberedDownContent?.invoke(mod, insets, prg)
            SwipeDirection.Left -> rememberedLeftContent?.invoke(mod, insets, prg)
            SwipeDirection.Right -> rememberedRightContent?.invoke(mod, insets, prg)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = state.size.width.toInt(), y = 0) }
    ) {
        if (dir != SwipeDirection.Up) rememberedUpContent?.invoke(modifier, insets, 0f)
        if (dir != SwipeDirection.Down) rememberedDownContent?.invoke(modifier, insets, 0f)
        if (dir != SwipeDirection.Left) rememberedLeftContent?.invoke(modifier, insets, 0f)
        if (dir != SwipeDirection.Right) rememberedRightContent?.invoke(modifier, insets, 0f)
    }

}

private fun Modifier.homePageAnimation(
    state: LauncherScaffoldState,
): Modifier {
    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then Modifier
            .offset {
                IntOffset(
                    x = if (state.currentDragDirection?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                    y = if (state.currentDragDirection?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
                )
            }
            .alpha(1f - state.currentProgress)
    }
    return this then Modifier.offset {
        IntOffset(
            x = if (state.currentDragDirection?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
            y = if (state.currentDragDirection?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
        )
    }
}

private fun Modifier.secondaryPageAnimation(
    state: LauncherScaffoldState,
): Modifier {
    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then Modifier
            .offset {
                IntOffset(
                    x = if (state.currentDragDirection?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                    y = if (state.currentDragDirection?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
                )
            }
            .alpha(state.currentProgress)
    }
    return this then Modifier
        .offset {
            when (state.currentDragDirection) {
                SwipeDirection.Up -> IntOffset(0, state.size.height.toInt())
                SwipeDirection.Down -> IntOffset(0, -state.size.height.toInt())
                SwipeDirection.Left -> IntOffset(state.size.width.toInt(), 0)
                SwipeDirection.Right -> IntOffset(-state.size.width.toInt(), 0)
                null -> IntOffset.Zero
            }
        }
        .offset {
            IntOffset(
                x = if (state.currentDragDirection?.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                y = if (state.currentDragDirection?.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
            )
        }
}