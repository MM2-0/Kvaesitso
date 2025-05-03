package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.searchbar.LauncherSearchBar
import kotlinx.coroutines.delay
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

internal data class ScaffoldGesture(
    val component: ScaffoldComponent,
    val animation: ScaffoldAnimation,
)

enum class SearchBarPosition {
    Top,
    Bottom,
}

internal data class ScaffoldConfiguration(
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

internal class LauncherScaffoldState(
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
    var isSettledOnSecondaryPage by mutableStateOf(false)
        private set

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
            if (isSettledOnSecondaryPage) {
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

    val currentComponent by derivedStateOf {
        val dir = currentDragDirection ?: return@derivedStateOf null
        config[dir]?.component
    }

    private val animatable =
        Animatable<Offset, AnimationVector2D>(Offset.Zero, Offset.VectorConverter)

    suspend fun onDragStarted() {
        if (locked) return
        animatable.stop()
    }

    fun onDrag(offset: Offset) {
        if (locked) return
        if (currentDragDirection == null || (!isSettledOnSecondaryPage && currentOffset.x.absoluteValue <= touchSlop && currentOffset.y.absoluteValue <= touchSlop)) {
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
        if (locked) return
        if (currentDragDirection == null) {
            currentOffset = Offset.Zero
        }
        val direction = currentDragDirection ?: return
        val offset = currentOffset
        val wasPageOpen = isSettledOnSecondaryPage

        val gesture = config[direction] ?: return

        if (gesture.animation == ScaffoldAnimation.Rubberband) {
            performRubberbandFling(direction, offset, velocity)
        } else if (gesture.animation == ScaffoldAnimation.Push) {
            performPushFling(direction, offset, velocity)
        }

        if (isSettledOnSecondaryPage != wasPageOpen) {
            if (wasPageOpen) {
                config.homeComponent.onMount()
                gesture.component.onUnmount()
            } else {
                gesture.component.onMount()
                config.homeComponent.onUnmount()
            }
        }

        if (!gesture.component.permanent) {
            delay(gesture.component.resetDelay)
            isSettledOnSecondaryPage = false
            currentOffset = Offset.Zero
        }
        if (!isSettledOnSecondaryPage) currentDragDirection = null
    }

    private suspend fun performRubberbandFling(
        direction: SwipeDirection,
        offset: Offset,
        velocity: Velocity
    ) {

        if (offset.x <= -rubberbandThreshold || offset.x < 0f && velocity.x < -minFlingVelocity) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.x >= rubberbandThreshold || offset.x > 0f && velocity.x > minFlingVelocity) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.y <= -rubberbandThreshold || offset.y < 0f && velocity.y < -minFlingVelocity) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
        } else if (offset.y >= rubberbandThreshold || offset.y > 0f && velocity.y > minFlingVelocity) {
            isSettledOnSecondaryPage = !isSettledOnSecondaryPage
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

        isSettledOnSecondaryPage = targetOffset != Offset.Zero

        animatable.snapTo(currentOffset)
        animatable.animateTo(
            targetOffset,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentOffset = this.value
        }
    }

    private var locked = false

    /**
     * Disables all gestures.
     */
    fun lock() {
        locked = true
    }
    /**
     * Re-enables all gestures.
     */
    fun unlock() {
        locked = false
    }
}

@Composable
internal fun LauncherScaffold(
    config: ScaffoldConfiguration = remember {
        ScaffoldConfiguration(
            homeComponent = ClockWidgetComponent,
            swipeDown = ScaffoldGesture(
                component = SearchComponent,
                animation = ScaffoldAnimation.Rubberband,
            ),
            swipeLeft = ScaffoldGesture(
                component = ScreenOffComponent,
                animation = ScaffoldAnimation.Push,
            ),
            swipeRight = ScaffoldGesture(
                component = WidgetsComponent,
                animation = ScaffoldAnimation.Push,
            ),
            swipeUp = ScaffoldGesture(
                component = WidgetsComponent,
                animation = ScaffoldAnimation.Push,
            )
        )
    }
) {

    val searchBarHeight by remember {
        derivedStateOf {
            64.dp
        }
    }

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
            if (state.currentProgress >= 0f && state.currentProgress <= 1f) {
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
            val insets = WindowInsets.systemBars.asPaddingValues().let {
                PaddingValues(
                    start = it.calculateStartPadding(LocalLayoutDirection.current),
                    end = it.calculateEndPadding(LocalLayoutDirection.current),
                    top = it.calculateTopPadding() + if (config.searchBarPosition == SearchBarPosition.Top) searchBarHeight else 0.dp,
                    bottom = it.calculateBottomPadding() + if (config.searchBarPosition == SearchBarPosition.Bottom) searchBarHeight else 0.dp
                )
            }

            config.homeComponent.Component(
                Modifier
                    .fillMaxSize()
                    .homePageAnimation(state),
                insets,
                state
            )

            SecondaryPage(
                state = state,
                config = config,
                modifier = Modifier
                    .fillMaxSize(),
                insets = insets,
            )

            Box(
                modifier = Modifier.fillMaxSize().searchBarAnimation(state)
            ) {
                LauncherSearchBar(
                    modifier = Modifier.align(if (config.searchBarPosition == SearchBarPosition.Top) Alignment.TopCenter else Alignment.BottomCenter),
                    style = SearchBarStyle.Solid,
                    focused = false,
                    actions = emptyList(),
                    level = { SearchBarLevel.Raised },
                    onFocusChange = {},
                )
            }
        }
    }
}

@Composable
private fun SecondaryPage(
    state: LauncherScaffoldState,
    config: ScaffoldConfiguration,
    modifier: Modifier = Modifier,
    insets: PaddingValues,
) {
    val components = remember(config) {
        setOfNotNull(
            config.swipeUp?.component,
            config.swipeDown?.component,
            config.swipeLeft?.component,
            config.swipeRight?.component,
            config.doubleTap?.component,
            config.longPress?.component,
        )
    }

    val composables = remember(config) {
        components.associateWith {
            movableContentOf<Modifier, PaddingValues, LauncherScaffoldState> { modifier, insets, state ->
                it.Component(
                    modifier = modifier,
                    insets = insets,
                    state = state
                )
            }
        }
    }

    val component = state.currentComponent

    if (component != null) {

        val mod = modifier
            .fillMaxSize()
            .secondaryPageAnimation(state, MaterialTheme.colorScheme.surfaceContainer)
        val composable = composables[component]

        composable?.invoke(mod, insets, state)
    }

    // Keep other components alive, but out of the viewport
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = state.size.width.toInt(), y = 0) }
    ) {
        for ((k, v) in composables) {
            if (k == component) continue
            v.invoke(modifier, insets, state)
        }
    }

}

private fun Modifier.homePageAnimation(
    state: LauncherScaffoldState,
): Modifier {
    val dir = state.currentDragDirection ?: return this
    val component = state.currentComponent ?: return this

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.homePageModifier(
            state,
            Modifier
                .offset {
                    IntOffset(
                        x = if (dir.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
                        y = if (dir.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
                    )
                }
                .alpha(1f - state.currentProgress))
    }
    return this then component.homePageModifier(state, Modifier.offset {
        IntOffset(
            x = if (dir.orientation == Orientation.Horizontal) state.currentOffset.x.toInt() else 0,
            y = if (dir.orientation == Orientation.Vertical) state.currentOffset.y.toInt() else 0
        )
    })
}

private fun Modifier.secondaryPageAnimation(
    state: LauncherScaffoldState,
    backgroundColor: Color,
): Modifier {
    val dir = state.currentDragDirection ?: return this
    val component = state.currentComponent ?: return this

    val background =
        if (component.drawBackground == true) backgroundColor.copy(alpha = 0.85f * state.currentProgress) else Color.Transparent

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then Modifier
            .background(background)
            .graphicsLayer {
                translationX =
                    if (dir.orientation == Orientation.Horizontal) state.currentOffset.x else 0f
                translationY =
                    if (dir.orientation == Orientation.Vertical) state.currentOffset.y else 0f
                if (state.isSettledOnSecondaryPage) {
                    alpha = (state.currentProgress).coerceAtMost(1f)
                    scaleX = 1f - ((1f - state.currentProgress) * 0.03f)
                    scaleY = 1f - ((1f - state.currentProgress) * 0.03f)
                } else {
                    alpha = (state.currentProgress * 2f - 1f).coerceAtLeast(0f)
                }
            }

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
        .background(background)
}

private fun Modifier.searchBarAnimation(
    state: LauncherScaffoldState,
): Modifier {
    val component = state.currentComponent ?: return this
    val dir = state.currentDragDirection ?: return this

    if (state.currentAnimation == ScaffoldAnimation.Rubberband) {
        return this then component.searchBarModifier(
            state,
            Modifier.graphicsLayer {
                translationX =
                    if (dir.orientation == Orientation.Horizontal) state.currentOffset.x * 0.5f else 0f
                translationY =
                    if (dir.orientation == Orientation.Vertical) state.currentOffset.y * 0.5f else 0f
            }
        )
    }

    return this then component.searchBarModifier(state, Modifier)
}