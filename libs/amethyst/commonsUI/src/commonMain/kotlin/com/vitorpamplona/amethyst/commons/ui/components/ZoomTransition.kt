/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import net.engawapg.lib.zoomable.ZoomState

/**
 * Where a [ZoomTransition] starts and ends, in window coordinates: the thumbnail's whole rect
 * ([bounds]) and the part of it that was on screen ([visibleBounds]). They differ when the
 * thumbnail is scrolled partly out of a feed or sits under a bar: the media is laid over the whole
 * thumbnail, so the part on screen lines up with what the feed showed, and shows only through a
 * window that grows out of [visibleBounds], so nothing is drawn over the bars. Thumbnails get one
 * through a [ZoomSourceAnchor].
 */
@Immutable
data class ZoomSource(
    val bounds: Rect,
    val visibleBounds: Rect = bounds,
) {
    fun translate(
        offsetX: Float,
        offsetY: Float,
    ) = ZoomSource(bounds.translate(offsetX, offsetY), visibleBounds.translate(offsetX, offsetY))

    companion object {
        /** No thumbnail on screen to grow out of or shrink into: the transition fades instead. */
        val Offscreen = ZoomSource(Rect.Zero)
    }
}

/**
 * The part of the window, in window coordinates, where a screen's content can be seen. A scaffold
 * that lays its content out under bars drawn on top of it (`DisappearingScaffold`) provides one
 * through [LocalZoomViewport] that leaves out what its bars cover right now, so a thumbnail half
 * under the top bar opens from the half that was showing instead of being drawn over the bar.
 */
fun interface ZoomViewport {
    fun boundsInWindow(): Rect?
}

val LocalZoomViewport = staticCompositionLocalOf<ZoomViewport?> { null }

/**
 * Tracks a thumbnail so a viewer can grow out of it: put [zoomSourceAnchor] on the thumbnail and
 * hand this to the viewer, which reads the [ZoomSource] with [capture] when it opens and again
 * when it closes, so both ends match what is on screen at that moment (bars slide without moving
 * the feed, so a position stored on the last scroll can be stale).
 */
@Stable
class ZoomSourceAnchor internal constructor() {
    internal var coordinates: LayoutCoordinates? = null
    internal var viewport: ZoomViewport? = null

    /** Where the thumbnail is now, or null when it is not laid out. */
    fun capture(): ZoomSource? {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return null
        return coordinates.zoomSource(viewport?.boundsInWindow())
    }
}

/**
 * Tracks a thumbnail. With a [key] (the media it shows), it also joins the [LocalZoomSourceGroup]
 * around it, so a viewer opened from a sibling thumbnail can close into this one when the user
 * pages to [key].
 */
@Composable
fun rememberZoomSourceAnchor(key: Any? = null): ZoomSourceAnchor {
    val viewport = LocalZoomViewport.current
    val anchor = remember { ZoomSourceAnchor() }.also { it.viewport = viewport }
    val group = LocalZoomSourceGroup.current
    DisposableEffect(group, key, anchor) {
        if (group != null && key != null) {
            group.add(key, anchor)
            onDispose { group.remove(key, anchor) }
        } else {
            onDispose {}
        }
    }
    return anchor
}

/**
 * The thumbnails of the media shown side by side (a note's text and galleries, a picture grid), by
 * the media each one shows. A viewer opened from one of them pages through all of the media; when
 * it closes on another one, it shrinks into that one's thumbnail rather than flying the media into
 * the place of the thumbnail it opened from. See [zoomSourceFor].
 */
@Stable
class ZoomSourceGroup {
    private val anchors = HashMap<Any, ZoomSourceAnchor>()

    fun anchorFor(key: Any): ZoomSourceAnchor? = anchors[key]

    internal fun add(
        key: Any,
        anchor: ZoomSourceAnchor,
    ) {
        anchors[key] = anchor
    }

    internal fun remove(
        key: Any,
        anchor: ZoomSourceAnchor,
    ) {
        if (anchors[key] === anchor) anchors.remove(key)
    }
}

val LocalZoomSourceGroup = staticCompositionLocalOf<ZoomSourceGroup?> { null }

/** Gathers the thumbnails [content] draws into one [ZoomSourceGroup]. */
@Composable
fun ProvideZoomSourceGroup(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalZoomSourceGroup provides remember { ZoomSourceGroup() }, content = content)
}

/**
 * Where a viewer that pages through several media closes while it shows [shown]: the thumbnail it
 * opened from ([openedAnchor], for [opened]), else [shown]'s own thumbnail in [group], else
 * [ZoomSource.Offscreen], a fade. Never the opening thumbnail for other media: that would fly it
 * into the wrong place.
 */
fun zoomSourceFor(
    shown: Any?,
    opened: Any?,
    openedAnchor: ZoomSourceAnchor?,
    group: ZoomSourceGroup?,
): ZoomSource? =
    if (shown == null || shown == opened) {
        openedAnchor?.capture()
    } else {
        group?.anchorFor(shown)?.capture() ?: ZoomSource.Offscreen
    }

/** Marks the thumbnail [anchor] tracks: the media's clipped box, before any padding around it. */
fun Modifier.zoomSourceAnchor(anchor: ZoomSourceAnchor): Modifier = onPlaced { anchor.coordinates = it }

/**
 * This layout's [ZoomSource], seen through [viewport]. [boundsInWindow] alone is clipped by every
 * ancestor, so for a thumbnail cut off at the top or bottom of a list it is only the visible slice,
 * and it is not clipped by bars drawn over the list, which [viewport] accounts for.
 */
fun LayoutCoordinates.zoomSource(viewport: Rect? = null): ZoomSource {
    val root = findRootCoordinates()
    val local = root.localBoundingBoxOf(this, clipBounds = false)
    val topLeft = root.localToWindow(local.topLeft)
    val bottomRight = root.localToWindow(local.bottomRight)
    val visible = boundsInWindow()
    return ZoomSource(
        bounds = Rect(topLeft, bottomRight),
        visibleBounds = if (viewport != null) visible.intersect(viewport) else visible,
    )
}

/**
 * The transition of a full-screen media viewer: the media grows out of the thumbnail that was
 * clicked ([sourceBounds], in window coordinates) to its place in the viewer, and shrinks back
 * into it on dismiss. A window clipped to the visible part of the thumbnail (with its
 * [sourceCornerRadius]) opens up to the whole viewer alongside, so the transition starts from and
 * ends on exactly what was on screen. Without a [sourceBounds], or when none of the thumbnail is
 * on screen, it is a plain fade.
 *
 * Use [rememberZoomTransition]. Put [zoomTransitionContainer] on the viewer's fill-size container,
 * [zoomTransitionLayer] on the fill-size box that holds the media (and nothing that should stay
 * put, like controls), report the media's layout with [onMediaPositioned], and fade the viewer's
 * backdrop and controls with [progress]. Dismiss through [dismiss], which plays the exit first.
 */
@Stable
class ZoomTransition internal constructor(
    private val source: () -> ZoomSource?,
    val sourceCornerRadius: Dp,
) {
    /** Where the thumbnail was when the viewer opened, then where it is when the viewer closes. */
    var sourceBounds by mutableStateOf(source())
        private set

    /** 0 at the thumbnail, 1 in the viewer. */
    internal val animatable = Animatable(0f)

    /** The transition's progress, for the backdrop and the controls to fade with. */
    val progress: Float get() = animatable.value

    // Natural layout bounds of the media on screen in the viewer: the "target" of the grow
    // animation, so the media itself — not the viewer's viewport — lines up with the thumbnail.
    internal var mediaBounds by mutableStateOf<Rect?>(null)

    internal var isExiting by mutableStateOf(false)

    internal val layerSpace = LayerSpace()

    /** Plays the exit, then calls the dismiss callback given to [rememberZoomTransition]. */
    fun dismiss() {
        if (isExiting) return
        // The thumbnail may have moved under the viewer (insets changing as the viewer hid the
        // system bars, a bar sliding back): shrink into where it is now. Keep the old one if it
        // is gone.
        source()?.let { sourceBounds = it }
        isExiting = true
    }

    /**
     * Reports where the media sits, from inside [zoomTransitionLayer]: its [coordinates] and, for a
     * [ContentScale.Fit] image whose layout is larger than what it paints, its [aspectRatio].
     */
    fun onMediaPositioned(
        coordinates: LayoutCoordinates,
        aspectRatio: Float?,
    ) {
        layerSpace.untransformedBounds(coordinates)?.let { updateMediaBounds(it.fitAspectRatio(aspectRatio)) }
    }

    // Accept the first set of valid bounds unconditionally. Subsequent updates are only accepted
    // while the animation is idle, so a layout change mid-transition (e.g. an async image
    // finishing loading, or a pager settling) can't re-target the transform and cause a hiccup.
    private fun updateMediaBounds(newBounds: Rect) {
        if (newBounds.width > 0f && newBounds.height > 0f) {
            val current = mediaBounds
            if (current == null) {
                mediaBounds = newBounds
            } else if (!animatable.isRunning && current != newBounds) {
                mediaBounds = newBounds
            }
        }
    }
}

/** A [ZoomTransition] out of the thumbnail [anchor] tracks, or a fade without one. */
@Composable
fun rememberZoomTransition(
    anchor: ZoomSourceAnchor?,
    sourceCornerRadius: Dp = 0.dp,
    onDismissed: () -> Unit,
): ZoomTransition = rememberZoomTransition({ anchor?.capture() }, sourceCornerRadius, onDismissed)

/**
 * A [ZoomTransition] that grows in as soon as the media has reported its bounds (300ms) and, after
 * [ZoomTransition.dismiss], shrinks back (250ms) and then calls [onDismissed]. [source] is read
 * when the viewer opens and again when it closes; null makes it a fade.
 */
@Composable
fun rememberZoomTransition(
    source: () -> ZoomSource?,
    sourceCornerRadius: Dp = 0.dp,
    onDismissed: () -> Unit,
): ZoomTransition {
    val currentSource by rememberUpdatedState(source)
    val transition = remember { ZoomTransition({ currentSource() }, sourceCornerRadius) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    // Start the enter animation as soon as valid media bounds are available. Without this gate,
    // the animation can begin before the media has reported real bounds and the layer falls back
    // to its alpha-only branch.
    LaunchedEffect(transition) {
        snapshotFlow { transition.mediaBounds }
            .filter { it != null && it.width > 0f && it.height > 0f }
            .first()
        transition.animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(transition.isExiting) {
        if (transition.isExiting) {
            transition.animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
            currentOnDismissed()
        }
    }

    return transition
}

/** Marks the viewer's untransformed, fill-size container: the space the media is measured in. */
fun Modifier.zoomTransitionContainer(transition: ZoomTransition): Modifier = onPlaced { transition.layerSpace.parent = it }

/**
 * The layer that grows out of the thumbnail: put it on the fill-size box holding the media. Only
 * this layer scales and translates, so controls drawn outside it stay put. [zoomState] is the
 * user's pinch zoom on the media, if any, so dismissing a zoomed-in image shrinks from what is on
 * screen instead of jumping.
 */
fun Modifier.zoomTransitionLayer(
    transition: ZoomTransition,
    zoomState: () -> ZoomState? = { null },
): Modifier =
    graphicsLayer {
        val source = transition.sourceBounds
        val src = source?.bounds
        val window = source?.visibleBounds
        val img = transition.mediaBounds
        if (src != null && window != null && img != null && src.hasArea() && window.hasArea() && img.hasArea()) {
            // Account for user-applied zoom: the exit animation must start from the visible
            // bounds, not the unzoomed layout bounds — otherwise dismissing a zoomed-in image jumps.
            val zoomed = img.zoomedBy(zoomState())
            // Uniform scale so non-square images keep their aspect ratio during the grow
            // animation. The image covers the whole thumbnail, even where it was scrolled out of
            // view, so the part on screen lines up with what the feed showed; the rest is clipped.
            val startScale = coverScale(src, zoomed)
            val p = transition.progress
            val scale = lerp(startScale, 1f, p)
            val tx = lerp(src.center.x - startScale * zoomed.center.x, 0f, p)
            val ty = lerp(src.center.y - startScale * zoomed.center.y, 0f, p)

            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = tx
            translationY = ty

            if (p < 1f) {
                // The thumbnail may be a crop of the image (e.g. a square gallery cell showing a
                // 4:3 photo), and only part of it may be on screen (cut off at the top or bottom of
                // the feed). Clip to a window that morphs from the visible part of the thumbnail
                // into the whole viewport, so the transition opens from and closes into exactly what
                // was on screen. The viewport (not the measured image) is the end state so there is nothing
                // to snap when the clip turns off at p = 1, where pager neighbours must show.
                // Window coordinates, mapped back into this layer's pre-transform space. A layer
                // outline, not a draw-phase clip, so animating it doesn't re-record the pager's
                // display list every frame.
                //
                // The window keeps the thumbnail's rounded corners and squares them off as it
                // grows, reaching 0 exactly at full screen (and rounding back on the way out),
                // instead of snapping square on the first frame. An edge the feed cut off was
                // square on screen, so the corners on it stay square.
                val radius = lerp(transition.sourceCornerRadius.toPx(), 0f, p) / scale
                val leftCut = window.left > src.left + 0.5f
                val topCut = window.top > src.top + 0.5f
                val rightCut = window.right < src.right - 0.5f
                val bottomCut = window.bottom < src.bottom - 0.5f
                shape =
                    RectClipShape(
                        Rect(
                            left = (lerp(window.left, 0f, p) - tx) / scale,
                            top = (lerp(window.top, 0f, p) - ty) / scale,
                            right = (lerp(window.right, size.width, p) - tx) / scale,
                            bottom = (lerp(window.bottom, size.height, p) - ty) / scale,
                        ),
                        topLeftRadius = if (topCut || leftCut) 0f else radius,
                        topRightRadius = if (topCut || rightCut) 0f else radius,
                        bottomRightRadius = if (bottomCut || rightCut) 0f else radius,
                        bottomLeftRadius = if (bottomCut || leftCut) 0f else radius,
                    )
                clip = true
            }
        } else {
            // No source bounds: fall back to a plain fade.
            alpha = transition.progress
        }
    }
        // After the graphicsLayer: these coordinates live inside the layer.
        .onPlaced { transition.layerSpace.layer = it }

private fun Rect.hasArea() = width > 0f && height > 0f

/**
 * Maps the media's coordinates to window space as if the grow/shrink transform were not applied.
 * [parent] is the untransformed container; [layer] is the fill-size box inside the animated
 * graphicsLayer, sitting at the parent's origin.
 *
 * The media must be measured in the transformed layer's own (pre-transform) space.
 * boundsInWindow() of anything inside the layer already includes the grow transform, and every
 * transform change re-fires the media's onGloballyPositioned. Measured that way, the bounds read
 * right after the first transform is applied are the thumbnail-sized ones, so the next frame
 * computes a start scale of ~1: the image pops to full size and only the clip window animates.
 */
internal class LayerSpace {
    var parent: LayoutCoordinates? = null
    var layer: LayoutCoordinates? = null

    fun untransformedBounds(content: LayoutCoordinates): Rect? {
        val parent = parent ?: return null
        val layer = layer ?: return null
        if (!parent.isAttached || !layer.isAttached || !content.isAttached) return null
        // Unclipped: the layer clips to the morphing thumbnail window while animating.
        return layer.localBoundingBoxOf(content, clipBounds = false).translate(parent.positionInWindow())
    }
}

/**
 * The rect a [ContentScale.Fit] image of [aspectRatio] actually paints inside this layout rect.
 * The media's layout fills the width, so a tall image is pillarboxed inside it; scaling the layout
 * rect onto the thumbnail would leave it at ~1x.
 */
internal fun Rect.fitAspectRatio(aspectRatio: Float?): Rect {
    if (aspectRatio == null || aspectRatio <= 0f || !hasArea()) return this
    return if (width / height > aspectRatio) {
        val fittedWidth = height * aspectRatio
        Rect(center.x - fittedWidth / 2f, top, center.x + fittedWidth / 2f, bottom)
    } else {
        val fittedHeight = width / aspectRatio
        Rect(left, center.y - fittedHeight / 2f, right, center.y + fittedHeight / 2f)
    }
}

/** The image's on-screen bounds after the user's pinch zoom: it scales uniformly around the layout center, then offsets. */
private fun Rect.zoomedBy(zoom: ZoomState?): Rect {
    val zScale = zoom?.scale ?: 1f
    val halfWidth = width * zScale / 2f
    val halfHeight = height * zScale / 2f
    val centerX = center.x + (zoom?.offsetX ?: 0f)
    val centerY = center.y + (zoom?.offsetY ?: 0f)
    return Rect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
}

/** Uniform scale at which [image] covers [source] in both dimensions. */
internal fun coverScale(
    source: Rect,
    image: Rect,
): Float = maxOf(source.width / image.width, source.height / image.height)

/** Clips a layer to a fixed [rect] in its own coordinates, regardless of the layer's size. */
private class RectClipShape(
    private val rect: Rect,
    private val topLeftRadius: Float,
    private val topRightRadius: Float,
    private val bottomRightRadius: Float,
    private val bottomLeftRadius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        if (topLeftRadius > 0f || topRightRadius > 0f || bottomRightRadius > 0f || bottomLeftRadius > 0f) {
            Outline.Rounded(
                RoundRect(
                    rect,
                    CornerRadius(topLeftRadius),
                    CornerRadius(topRightRadius),
                    CornerRadius(bottomRightRadius),
                    CornerRadius(bottomLeftRadius),
                ),
            )
        } else {
            Outline.Rectangle(rect)
        }
}
