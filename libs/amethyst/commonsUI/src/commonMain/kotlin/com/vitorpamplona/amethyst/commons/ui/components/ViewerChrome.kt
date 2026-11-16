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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size15dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Chrome shared by the full-screen media viewers and the carousels inside them: how the
// controls auto-hide, and how far they sit from a screen edge. The platform half (hiding
// the OS bars) stays with each front end's viewer.

// Opening churn -- insets arriving, then the bars being hidden -- must not look like a user
// gesture, so the row snaps through it and only animates afterwards.
private const val CONTROLS_SETTLE_BEFORE_ANIMATING_MS = 350L

// Roughly the system bars' own show/hide duration, so the row travels with them rather than
// trailing after they have already arrived.
private const val CONTROLS_SLIDE_MS = 200

// Keeps the row off the screen edge -- and off the rounded corners -- while the bars are hidden.
private val VIEWER_CHROME_EDGE_GAP = 16.dp

// How long the controls stay up before the viewer fades them out on its own.
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 2000L

/**
 * Visibility of the viewer controls: they start on screen, fade out on their own after
 * [CONTROLS_AUTO_HIDE_DELAY_MS], and the caller flips the returned state on tap.
 *
 * [holdOpen] freezes the timer while something anchored to the controls -- the share sheet, say --
 * is up, and re-arms it once that closes. [armed] withholds the countdown until there is something
 * to look at, so a viewer that spends three seconds fetching its media doesn't reveal the first
 * frame with the controls already gone.
 *
 * A tap that brings the controls back deliberately gets no timer: the user asked for them, so they
 * stay until tapped away. That is why the countdown races the controls going away rather than just
 * sleeping -- a timer left over from an earlier show would otherwise wipe controls the user tapped
 * back up in the meantime.
 */
@Composable
fun rememberViewerControlsVisibility(
    holdOpen: Boolean,
    armed: Boolean = true,
): MutableState<Boolean> {
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(armed, holdOpen) {
        if (!armed || holdOpen) return@LaunchedEffect

        val hiddenFirst =
            withTimeoutOrNull(CONTROLS_AUTO_HIDE_DELAY_MS) {
                snapshotFlow { visible.value }.first { !it }
            }

        if (hiddenFirst == null) visible.value = false
    }

    return visible
}

/**
 * How far the viewer chrome sits from a screen edge: the system bar's own height while the bar is
 * on screen, and a thin constant once it is hidden -- so the chrome follows the bar instead of
 * reserving space for one that is not there.
 *
 * This only works because the viewer asks for BEHAVIOR_DEFAULT rather than transient bars. Under
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE a peeked bar is painted over the content and dispatches no
 * insets at all -- `systemBars` stays 0 and `isVisible` stays false the whole time it is on screen
 * -- so nothing here could react to it.
 *
 * The value is animated, but snapped for [CONTROLS_SETTLE_BEFORE_ANIMATING_MS] after the chrome
 * appears. Opening moves the inset twice for reasons the user did not cause: the window has not
 * been told its insets yet (they read 0), and ImmersiveSystemBarsEffect hides the bars from a
 * DisposableEffect that runs after composition. Animating either would play a slide on open.
 */
@Composable
fun animatedViewerChromeInset(atBottom: Boolean): Dp {
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars
    val barPx = if (atBottom) bars.getBottom(density) else bars.getTop(density)
    val target = with(density) { maxOf(barPx, VIEWER_CHROME_EDGE_GAP.roundToPx()).toDp() }

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CONTROLS_SETTLE_BEFORE_ANIMATING_MS)
        settled = true
    }

    val animated by animateDpAsState(
        targetValue = target,
        animationSpec = if (settled) tween(durationMillis = CONTROLS_SLIDE_MS) else snap(),
        label = "viewerChromeInset",
    )
    return animated
}

/**
 * Lays a viewer control row along a screen edge -- the top by default, the bottom when [atBottom].
 *
 * The viewer hides the system bars, so the row would otherwise sit against the screen edge. It
 * takes its distance from [animatedViewerChromeInset], which follows the bar on and off screen
 * rather than permanently reserving room for it.
 *
 * Horizontal display-cutout insets are still applied outright: a landscape notch eats into the
 * sides whatever the bars are doing. The top cutout is deliberately not applied, because on a
 * punch-hole device it is a centred hole that the edge-anchored buttons are nowhere near -- and
 * honouring it as a full-width top inset would push them down by the height of a camera they do
 * not overlap.
 *
 * The row also holds a button's height whatever it carries, so content that outlives the buttons
 * doesn't shift as they come and go.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ViewerControlsRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = spacedBy(Size10dp),
    atBottom: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    // systemBars is visibility-aware under BEHAVIOR_DEFAULT: 0 while the bars are hidden, and the
    // real bar size once the user swipes them in -- so the controls follow them instead of sitting
    // under a bar or reserving space for one that is not there. The 16dp floor keeps them clear of
    // the rounded corners while hidden. Animated so the row slides rather than jumps.
    val animatedInset = animatedViewerChromeInset(atBottom)
    Row(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(
                    top = if (atBottom) 0.dp else animatedInset,
                    bottom = if (atBottom) animatedInset else 0.dp,
                ).padding(horizontal = Size15dp, vertical = Size10dp)
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MinHeight),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Leaves the viewer. Always the first item in the control row. */
@Composable
fun ViewerBackButton(onDismiss: () -> Unit) {
    OutlinedButton(
        onClick = onDismiss,
        contentPadding = PaddingValues(horizontal = Size5dp),
        colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
            contentDescription = stringRes(Res.string.back),
        )
    }
}
