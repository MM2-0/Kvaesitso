package de.mm20.launcher2.ui.launcher

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    Fly,
    Fade,
    None,
}

data class ScaffoldGesture(
    val action: ScaffoldAction,
    val enterAnimation: ScaffoldAnimation,
    val exitAnimation: ScaffoldAnimation,
)

enum class SearchBarPosition {
    Top,
    Bottom,
}

data class ScaffoldConfiguration(
    val swipeUp: ScaffoldGesture? = null,
    val swipeDown: ScaffoldGesture? = null,
    val swipeLeft: ScaffoldGesture? = null,
    val swipeRight: ScaffoldGesture? = null,
    val doubleTap: ScaffoldGesture? = null,
    val longPress: ScaffoldGesture? = null,
    val searchBarPosition: SearchBarPosition = SearchBarPosition.Top,
)

enum class SwipeDirection(val orientation: Orientation) {
    Up(Orientation.Vertical),
    Down(Orientation.Vertical),
    Left(Orientation.Horizontal),
    Right(Orientation.Horizontal),
}

private class NgScaffoldState(
    private val config: ScaffoldConfiguration,
    private val size: Size,
    private val touchSlop: Float,
    private val minFlingVelocity: Float,
) {
    var currentDragDelta by mutableStateOf(Offset.Zero)
    var currentDragDirection by mutableStateOf<SwipeDirection?>(null)

    private val animatable =
        Animatable<Offset, AnimationVector2D>(Offset.Zero, Offset.VectorConverter)

    suspend fun onDragStarted() {
        animatable.stop()
    }

    fun performDrag(offset: Offset) {
        if (currentDragDelta.x.absoluteValue <= touchSlop && currentDragDelta.y.absoluteValue <= touchSlop) {
            currentDragDirection = getSwipeDirection(config, offset)
        }
        currentDragDelta = Offset(
            x = if (currentDragDirection?.orientation == Orientation.Vertical) 0f else
                (currentDragDelta.x + offset.x).coerceIn(
                    if (config.swipeLeft == null) 0f else -size.width,
                    if (config.swipeRight == null) 0f else size.width
                ),
            y = if (currentDragDirection?.orientation == Orientation.Horizontal) 0f else
                (currentDragDelta.y + offset.y).coerceIn(
                    if (config.swipeUp == null) 0f else -size.height,
                    if (config.swipeDown == null) 0f else size.height
                ),
        )
        Log.d("MM20", "currentDragDelta: $currentDragDelta, currentDragDirection: $currentDragDirection")
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

    suspend fun performFling(velocity: Velocity) {
        val targetOffset = when (currentDragDirection) {
            SwipeDirection.Up
                if velocity.y <= -minFlingVelocity ||
                        (currentDragDelta.y < size.height / -2f && velocity.y < minFlingVelocity)
                -> Offset(0f, -size.height)

            SwipeDirection.Down
                if velocity.y >= minFlingVelocity ||
                        (currentDragDelta.y > size.height / 2f && velocity.y > -minFlingVelocity)
                -> Offset(0f, size.height)

            SwipeDirection.Left
                if velocity.x <= -minFlingVelocity ||
                        (currentDragDelta.x < size.width / -2f && velocity.x < minFlingVelocity)
                -> Offset(-size.width, 0f)

            SwipeDirection.Right
                if velocity.x >= minFlingVelocity ||
                        (currentDragDelta.x > size.width / 2f && velocity.x > -minFlingVelocity)
                -> Offset(size.width, 0f)

            else -> Offset.Zero
        }
        animatable.snapTo(currentDragDelta)
        animatable.animateTo(
            targetOffset,
            initialVelocity = Offset(velocity.x, velocity.y),
        ) {
            currentDragDelta = this.value
        }
    }
}

@Composable
fun NgScaffold(
    config: ScaffoldConfiguration = ScaffoldConfiguration(
        swipeUp = ScaffoldGesture(
            action = ScaffoldAction.Widgets,
            enterAnimation = ScaffoldAnimation.Fly,
            exitAnimation = ScaffoldAnimation.Fly,
        ),
        swipeLeft = ScaffoldGesture(
            action = ScaffoldAction.Widgets,
            enterAnimation = ScaffoldAnimation.Fly,
            exitAnimation = ScaffoldAnimation.Fly,
        )
    )
) {

    BoxWithConstraints {
        val width = this.maxWidth
        val height = this.maxHeight
        val widthPx = width.toPixels()
        val heightPx = height.toPixels()

        val touchSlop = LocalViewConfiguration.current.touchSlop
        val minFlingVelocity = 125.dp.toPixels()

        val state = remember(widthPx, heightPx, touchSlop, minFlingVelocity, config) {
            NgScaffoldState(
                config = config,
                size = Size(widthPx, heightPx),
                touchSlop = touchSlop,
                minFlingVelocity = minFlingVelocity,
            )
        }

        val scope = rememberCoroutineScope()

        val draggableState = rememberDraggable2DState {
            state.performDrag(it)
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable2D(
                    state = draggableState,
                    onDragStarted = {
                        scope.launch {
                            state.onDragStarted()
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            state.performFling(velocity)
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = if (state.currentDragDirection?.orientation == Orientation.Horizontal) state.currentDragDelta.x.toInt() else 0,
                            y = if (state.currentDragDirection?.orientation == Orientation.Vertical) state.currentDragDelta.y.toInt() else 0
                        )
                    }
                    .background(Color.White)
            ) {
                Text("Drag me")
            }
        }
    }
}

private fun Modifier.scaffoldAnimation(
    direction: SwipeDirection,
    delta: Float,
    totalSize: Int,
) {

}