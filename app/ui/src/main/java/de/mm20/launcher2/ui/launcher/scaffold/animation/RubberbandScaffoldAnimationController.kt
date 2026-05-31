package de.mm20.launcher2.ui.launcher.scaffold.animation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldAnimation
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage
import kotlin.math.absoluteValue

internal class RubberbandScaffoldAnimationController(
    private val rubberbandThreshold: Float,
    private val velocityThreshold: Float,
) : ScaffoldAnimationController {
    override val animation: ScaffoldAnimation = ScaffoldAnimation.Rubberband

    override fun initialOffset(direction: Gesture, size: Size): Offset3D = Offset3D.Zero

    override fun calculateOffset(
        direction: Gesture,
        currentOffset: Offset3D,
        delta: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
        isAtTop: Boolean,
        isAtBottom: Boolean,
    ): Offset3D {
        val adjustedDelta = when {
            !isAtTop && !isAtBottom -> delta.copy(y = 0f)
            !isAtTop && isAtBottom -> delta.copy(y = delta.y.coerceAtMost(-currentOffset.y))
            else -> delta.copy(y = delta.y.coerceAtLeast(-currentOffset.y))
        }

        val threshold = rubberbandThreshold * 1.5f
        return when (direction) {
            Gesture.SwipeUp,
            Gesture.SwipeDown,
            -> currentOffset.copy(
                x = 0f,
                y = (currentOffset.y + adjustedDelta.y).coerceIn(-threshold, threshold),
            )

            Gesture.SwipeLeft,
            Gesture.SwipeRight,
            -> currentOffset.copy(
                x = (currentOffset.x + adjustedDelta.x).coerceIn(-threshold, threshold),
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
        val axisOffset = (offset.x + offset.y).absoluteValue.coerceAtMost(rubberbandThreshold)
        return when (currentPage) {
            ScaffoldPage.Secondary -> 1f - axisOffset / (rubberbandThreshold * 2f)
            ScaffoldPage.Home -> axisOffset / (rubberbandThreshold * 2f)
        }
    }

    override fun calculateTargetPage(
        direction: Gesture,
        offset: Offset3D,
        velocity: Velocity,
        currentPage: ScaffoldPage,
        size: Size,
    ): ScaffoldPage {

        val shouldToggle =
            offset.x <= -rubberbandThreshold || offset.x < 0f && velocity.x < -velocityThreshold ||
                    offset.x >= rubberbandThreshold || offset.x > 0f && velocity.x > velocityThreshold ||
                    offset.y <= -rubberbandThreshold || offset.y < 0f && velocity.y < -velocityThreshold ||
                    offset.y >= rubberbandThreshold || offset.y > 0f && velocity.y > velocityThreshold

        if (!shouldToggle) return currentPage

        return when (currentPage) {
            ScaffoldPage.Home -> ScaffoldPage.Secondary
            ScaffoldPage.Secondary -> ScaffoldPage.Home
        }
    }

    override fun calculateTargetOffset(
        direction: Gesture,
        targetPage: ScaffoldPage,
        size: Size,
    ): Offset3D = Offset3D.Zero

    override fun calculatePredictiveBackProgress(progress: Float): Float = 1f - progress
}