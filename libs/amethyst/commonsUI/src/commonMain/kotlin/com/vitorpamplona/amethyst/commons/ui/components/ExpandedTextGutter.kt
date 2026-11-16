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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/**
 * Where an expanded note text puts its "Show Less". [ExpandableRichTextViewer] registers how to
 * collapse itself while it shows its full text, and the note renders [ExpandedTextGutter] in its
 * author column, under the picture. A note taller than the feed can show at once gets the button
 * pinned in that column ([pinned]), so it can be collapsed from anywhere in it. A shorter one keeps
 * it at the end of the text, where "Show More" was.
 */
@Stable
class ExpandedTextGutterState {
    // A note can show several expandable texts (a poll and its options, a highlight and its comment).
    private var collapseActions by mutableStateOf(persistentListOf<() -> Unit>())

    val isExpanded: Boolean get() = collapseActions.isNotEmpty()

    /** True while the note is taller than the feed: "Show Less" is pinned in the gutter, not under the text. */
    var pinned by mutableStateOf(false)
        internal set

    // Set by the gutter while it is composed, which is whenever a text is expanded.
    internal var revealNote: (() -> Unit)? = null

    fun register(action: () -> Unit) {
        collapseActions = collapseActions.adding(action)
    }

    fun unregister(action: () -> Unit) {
        collapseActions = collapseActions.removing(action)
        if (collapseActions.isEmpty()) pinned = false
    }

    /** Scrolls the feed so the note's top is on screen, before one of its texts shrinks. */
    fun revealNoteTop() {
        revealNote?.invoke()
    }

    /** The pinned button folds every expanded text in the note. */
    fun collapseAll() {
        revealNoteTop()
        collapseActions.forEach { it() }
    }
}

/**
 * The gutter of the note being rendered, or null where there is none (quotes, chats, profiles):
 * [ExpandableRichTextViewer] then always puts its "Show Less" under the text.
 */
val LocalExpandedTextGutter = staticCompositionLocalOf<ExpandedTextGutterState?> { null }

private val GutterButtonSize = 44.dp
private val GutterButtonTopMargin = 4.dp

// The text drops its own "Show Less" while the gutter's is pinned. That button is shorter than
// this, so pinning cannot shrink the note back under the feed's height and flip it straight back.
private val PinHysteresis = 64.dp

/**
 * Emits nothing while the text is collapsed, so the note lays out no gutter at all. While expanded
 * it measures the note, and when the note is taller than the feed a faint rail runs down the column
 * with a quiet fold icon pinned to the top of what is visible of the note, until the note's bottom
 * pushes it up. "Show Less" is something people rarely need, so it stays out of the way.
 */
@Composable
fun ExpandedTextGutter(state: ExpandedTextGutterState) {
    if (!state.isExpanded) return
    StickyShowLessButton(state)
}

@Composable
private fun StickyShowLessButton(state: ExpandedTextGutterState) {
    val viewport = LocalZoomViewport.current
    val topBarHeight = rememberTopBarHeightPx()
    val feedHeight = rememberFeedHeightPx()
    val density = LocalDensity.current
    val hysteresis = remember(density) { with(density) { PinHysteresis.roundToPx() } }
    val tracker = remember { GutterTracker() }
    val dispatcher = remember { NestedScrollDispatcher() }

    DisposableEffect(state, tracker, dispatcher, viewport, topBarHeight) {
        state.revealNote = { tracker.revealNoteTop(dispatcher, viewport, topBarHeight) }
        onDispose { state.revealNote = null }
    }

    LaunchedEffect(state, tracker, feedHeight, hysteresis) {
        snapshotFlow { tracker.noteHeight }
            .filter { it > 0 }
            .collect { height ->
                state.pinned = if (state.pinned) height > feedHeight - hysteresis else height > feedHeight
            }
    }

    val railColor = MaterialTheme.colorScheme.subtleBorder

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(NoOpNestedScrollConnection, dispatcher)
                .onGloballyPositioned { tracker.onPositioned(it) }
                .drawBehind {
                    if (!state.pinned) return@drawBehind
                    val buttonHeight = GutterButtonSize.roundToPx()
                    val railTop = tracker.stickyOffset(viewport, buttonHeight, GutterButtonTopMargin.roundToPx()) + buttonHeight
                    if (railTop < size.height) {
                        val x = size.width / 2
                        drawLine(railColor, Offset(x, railTop.toFloat()), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    }
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!state.pinned) return@Box

        Box(
            modifier =
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, tracker.stickyOffset(viewport, placeable.height, GutterButtonTopMargin.roundToPx()))
                        }
                    }.size(GutterButtonSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { state.collapseAll() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.UnfoldLess,
                contentDescription = stringRes(Res.string.show_less),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

internal object NoOpNestedScrollConnection : NestedScrollConnection

private class GutterTracker {
    private var coordinates: LayoutCoordinates? = null

    // Bumped every time the gutter moves on screen, so the button's placement runs again.
    private var moves by mutableIntStateOf(0)

    /** The whole note's height: the button only shows when the note is taller than the feed. */
    var noteHeight by mutableIntStateOf(0)
        private set

    fun onPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        moves++
        noteHeight = (coordinates.parentLayoutCoordinates ?: coordinates).size.height
    }

    /** How far down the gutter the button sits: the visible top, kept inside the gutter. */
    fun stickyOffset(
        viewport: ZoomViewport?,
        buttonHeight: Int,
        margin: Int,
    ): Int {
        moves
        val gutter = coordinates?.takeIf { it.isAttached } ?: return 0
        val hidden = gutter.visibleTopInWindow(viewport) - gutter.positionInWindow().y
        val maxOffset = (gutter.size.height - buttonHeight).coerceAtLeast(0)
        return (hidden.roundToInt() + margin).coerceIn(0, maxOffset)
    }

    fun revealNoteTop(
        dispatcher: NestedScrollDispatcher,
        viewport: ZoomViewport?,
        topBarHeight: Float,
    ) {
        val gutter = coordinates?.takeIf { it.isAttached } ?: return
        dispatcher.revealTopOf(gutter.parentLayoutCoordinates ?: gutter, viewport, topBarHeight)
    }
}

/**
 * Scrolls the list so [target]'s top is back on screen, before [target] shrinks. Without it, a text
 * collapsed from deep inside would leave the feed scrolled past its note, onto whatever came next.
 * The parent scrollable consumes what its children leave over, so a post-scroll dispatch moves the
 * list right away, before the collapse lays out. [dispatcher] must be attached with nestedScroll.
 *
 * When the list cuts [target] off, the list runs under the top bar and scrolling back up brings a
 * hidden bar back, so the top lands [topBarHeight] below the list's edge whether or not the bar
 * shows right now. When [target]'s top is inside the list, only a bar drawn over it can hide it.
 */
internal fun NestedScrollDispatcher.revealTopOf(
    target: LayoutCoordinates,
    viewport: ZoomViewport?,
    topBarHeight: Float,
) {
    if (!target.isAttached) return
    val top = target.positionInWindow().y
    val clippedTop = target.boundsInWindow().top
    val landAt =
        if (clippedTop > top + 0.5f) {
            clippedTop + topBarHeight
        } else {
            viewport?.boundsInWindow()?.top ?: return
        }
    val hidden = landAt - top
    if (hidden > 0f) {
        dispatchPostScroll(Offset.Zero, Offset(0f, hidden), NestedScrollSource.UserInput)
    }
}

/** The height of the bar the feed scrolls under, in pixels; zero outside the disappearing scaffold. */
@Composable
internal fun rememberTopBarHeightPx(): Float {
    val topPadding = LocalDisappearingScaffoldPadding.current.calculateTopPadding()
    val density = LocalDensity.current
    return remember(topPadding, density) { with(density) { topPadding.toPx() } }
}

/**
 * How much of the feed shows at once with its bars in place: the window minus the scaffold's bars,
 * or minus the system bars outside a disappearing scaffold, in pixels. Screens with their own
 * header (a profile's tabs) show a bit less than this.
 */
@Composable
private fun rememberFeedHeightPx(): Int {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val padding = LocalDisappearingScaffoldPadding.current
    val density = LocalDensity.current
    val systemBarsTop = WindowInsets.systemBars.getTop(density)
    val systemBarsBottom = WindowInsets.systemBars.getBottom(density)
    return remember(windowHeight, padding, density, systemBarsTop, systemBarsBottom) {
        val bars = with(density) { (padding.calculateTopPadding() + padding.calculateBottomPadding()).roundToPx() }
        windowHeight - if (bars > 0) bars else systemBarsTop + systemBarsBottom
    }
}

/**
 * Scrolls the feed back to a layout's top before it shrinks. The layout it is attached to with
 * [modifier] is the one revealed, unless a note's gutter reveals the whole note instead.
 */
internal class RevealTop {
    private val dispatcher = NestedScrollDispatcher()
    private var coordinates: LayoutCoordinates? = null
    var viewport: ZoomViewport? = null
    var topBarHeight: Float = 0f

    val modifier: Modifier =
        Modifier
            .nestedScroll(NoOpNestedScrollConnection, dispatcher)
            .onPlaced { coordinates = it }

    fun reveal() {
        coordinates?.let { dispatcher.revealTopOf(it, viewport, topBarHeight) }
    }
}

@Composable
internal fun rememberRevealTop(): RevealTop {
    val viewport = LocalZoomViewport.current
    val topBarHeight = rememberTopBarHeightPx()
    return remember { RevealTop() }.also {
        it.viewport = viewport
        it.topBarHeight = topBarHeight
    }
}

/** The top of what shows of this layout: clipped by its scrolling parents and by bars drawn over them. */
private fun LayoutCoordinates.visibleTopInWindow(viewport: ZoomViewport?): Float {
    val clipped = boundsInWindow().top
    val uncovered = viewport?.boundsInWindow()?.top ?: return clipped
    return maxOf(clipped, uncovered)
}
