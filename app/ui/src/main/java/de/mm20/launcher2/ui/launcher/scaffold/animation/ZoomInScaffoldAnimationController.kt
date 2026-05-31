package de.mm20.launcher2.ui.launcher.scaffold.animation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldAnimation
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage

internal object ZoomInScaffoldAnimationController : ScaffoldAnimationController {
    override val animation: ScaffoldAnimation = ScaffoldAnimation.ZoomIn

    override fun initialOffset(direction: Gesture, size: Size): Offset3D {
        return if (direction.orientation == null) Offset3D(z = 1f) else Offset3D.Zero
    }

    override fun calculateOffset(
        direction: Gesture,
        currentOffset: Offset3D,
        delta: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
        isAtTop: Boolean,
        isAtBottom: Boolean,
    ): Offset3D = currentOffset

    override fun calculateProgress(
        direction: Gesture,
        offset: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
    ): Float = offset.z.coerceIn(0f, 1f)

    override fun calculateTargetPage(
        direction: Gesture,
        offset: Offset3D,
        velocity: Velocity,
        currentPage: ScaffoldPage,
        size: Size,
    ): ScaffoldPage = currentPage

    override fun calculateTargetOffset(
        direction: Gesture,
        targetPage: ScaffoldPage,
        size: Size,
    ): Offset3D {
        return when (targetPage) {
            ScaffoldPage.Home -> Offset3D.Zero
            ScaffoldPage.Secondary -> Offset3D(z = 1f)
        }
    }

    override fun calculatePredictiveBackProgress(progress: Float): Float = 1f - progress
}



