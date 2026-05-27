package de.mm20.launcher2.ui.launcher.scaffold.animation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldAnimation
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage
import kotlin.math.absoluteValue

internal class PushScaffoldAnimationController(
    private val velocityThreshold: Float,
) : ScaffoldAnimationController {
    override val animation: ScaffoldAnimation = ScaffoldAnimation.Push

    override fun initialOffset(direction: Gesture, size: Size): Offset3D {
        return when (direction) {
            Gesture.SwipeRight -> Offset3D(x = -size.width)
            Gesture.SwipeLeft -> Offset3D(x = size.width)
            Gesture.SwipeUp -> Offset3D(y = -size.height)
            Gesture.SwipeDown -> Offset3D(y = size.height)
            else -> Offset3D.Zero
        }
    }

    override fun calculateOffset(
        direction: Gesture,
        currentOffset: Offset3D,
        delta: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
        isAtTop: Boolean,
        isAtBottom: Boolean,
    ): Offset3D {
        return when (direction) {
            Gesture.SwipeUp -> currentOffset.copy(
                x = 0f,
                y = (currentOffset.y + delta.y).coerceIn(-size.height, 0f),
            )

            Gesture.SwipeDown -> currentOffset.copy(
                x = 0f,
                y = (currentOffset.y + delta.y).coerceIn(0f, size.height),
            )

            Gesture.SwipeLeft -> currentOffset.copy(
                x = (currentOffset.x + delta.x).coerceIn(-size.width, 0f),
                y = 0f,
            )

            Gesture.SwipeRight -> currentOffset.copy(
                x = (currentOffset.x + delta.x).coerceIn(0f, size.width),
                y = 0f,
            )

            else -> currentOffset
        }
    }

    override fun calculateProgress(
        direction: Gesture,
        offset: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
    ): Float {
        return if (direction.orientation == Orientation.Horizontal) {
            (offset.x.absoluteValue / size.width).coerceIn(0f, 1f)
        } else {
            (offset.y.absoluteValue / size.height).coerceIn(0f, 1f)
        }
    }

    override fun calculateTargetPage(
        direction: Gesture,
        offset: Offset3D,
        velocity: Velocity,
        currentPage: ScaffoldPage,
        size: Size,
    ): ScaffoldPage {
        val lowerPage = when (direction) {
            Gesture.SwipeUp -> -size.height
            Gesture.SwipeDown -> 0f
            Gesture.SwipeLeft -> -size.width
            Gesture.SwipeRight -> 0f
            else -> return currentPage
        }
        val upperPage = if (direction.orientation == Orientation.Vertical) {
            lowerPage + size.height
        } else {
            lowerPage + size.width
        }
        val threshold = (upperPage + lowerPage) / 2f

        val target = if (direction.orientation == Orientation.Vertical) {
            if (offset.y > threshold && velocity.y > -velocityThreshold || velocity.y > velocityThreshold) {
                upperPage
            } else {
                lowerPage
            }
        } else {
            if (offset.x > threshold && velocity.x > -velocityThreshold || velocity.x > velocityThreshold) {
                upperPage
            } else {
                lowerPage
            }
        }

        return if (target == 0f) ScaffoldPage.Home else ScaffoldPage.Secondary
    }

    override fun calculateTargetOffset(
        direction: Gesture,
        targetPage: ScaffoldPage,
        size: Size,
    ): Offset3D {
        if (targetPage == ScaffoldPage.Home) return Offset3D.Zero

        return when (direction) {
            Gesture.SwipeLeft -> Offset3D(x = -size.width)
            Gesture.SwipeRight -> Offset3D(x = size.width)
            Gesture.SwipeUp -> Offset3D(y = -size.height)
            Gesture.SwipeDown -> Offset3D(y = size.height)
            else -> Offset3D.Zero
        }
    }

    override fun calculatePredictiveBackProgress(progress: Float): Float = 1f - progress * 0.1f
}