package de.mm20.launcher2.ui.launcher.scaffold.animation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import de.mm20.launcher2.ui.launcher.scaffold.Gesture
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldAnimation
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage

/**
 * Encapsulates animation-specific movement logic for launcher page transitions.
 *
 * Contract:
 * - x/y in [Offset3D] are screen-space offsets (px).
 * - z in [Offset3D] is normalized depth progress (typically 0..1).
 * - Drag and release are intentionally split:
 *   - [calculateOffset] for per-frame drag updates
 *   - [calculateTargetPage] and [calculateTargetOffset] for settle/fling behavior
 */
internal interface ScaffoldAnimationController {
    /** The animation flavor handled by this controller. */
    val animation: ScaffoldAnimation

    /**
     * Returns the initial displacement for a restored secondary page.
     *
     * Called when the scaffold state is reconstructed from saved state.
     */
    fun initialOffset(direction: Gesture, size: Size): Offset3D

    /**
     * Calculates the next displacement during a drag gesture.
     *
     * @param direction Locked gesture direction for the current interaction.
     * @param currentOffset Current displacement before applying [delta].
     * @param delta Per-frame pointer delta, mapped to 3D space.
     * @param currentPage Current settled page before drag update.
     * @param size Current scaffold size in px.
     * @param isAtTop Whether active content is scrolled to top.
     * @param isAtBottom Whether active content is scrolled to bottom.
     */
    fun calculateOffset(
        direction: Gesture,
        currentOffset: Offset3D,
        delta: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
        isAtTop: Boolean,
        isAtBottom: Boolean,
    ): Offset3D

    /**
     * Maps the current displacement to transition progress in range 0..1.
     *
     * 0 means home page, 1 means secondary page.
     */
    fun calculateProgress(
        direction: Gesture,
        offset: Offset3D,
        currentPage: ScaffoldPage,
        size: Size,
    ): Float

    /**
     * Decides the page that should be active after drag release/fling.
     */
    fun calculateTargetPage(
        direction: Gesture,
        offset: Offset3D,
        velocity: Velocity,
        currentPage: ScaffoldPage,
        size: Size,
    ): ScaffoldPage

    /**
     * Returns the settle target displacement for the given [targetPage].
     *
     * This value is used as animation target after release.
     */
    fun calculateTargetOffset(
        direction: Gesture,
        targetPage: ScaffoldPage,
        size: Size,
    ): Offset3D

    /**
     * Maps predictive-back progress to scaffold transition progress.
     *
     * Input and output are normalized in range 0..1.
     */
    fun calculatePredictiveBackProgress(progress: Float): Float
}