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
package com.vitorpamplona.amethyst.commons.ui.layouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Fraction at which the tracked in-app bar is considered fully settled into the hidden edge. */
private const val STATUS_BAR_HIDE_THRESHOLD = 0.999f

/**
 * Fraction the chrome must fall back below before the OS status bar reappears. Lower than
 * [STATUS_BAR_HIDE_THRESHOLD] on purpose: this hysteresis gap means the finger that stops a fast
 * scroll — which makes a tiny reverse reveal — does not immediately pop the status bar back. The
 * user has to deliberately scroll the chrome back by ~30% before the status bar returns.
 */
private const val STATUS_BAR_SHOW_THRESHOLD = 0.7f

/**
 * Hides the OS status bar once the tracked in-app bar has settled into the hidden edge, and shows it
 * the moment it starts coming back. The OS status bar is binary (cannot slide), so it is driven off
 * the collapse fraction with a near-1 threshold, which means it toggles on settle rather than
 * mid-drag.
 *
 * The status bar sits with the top in-app bar, so it tracks the top bar's collapse. The top and
 * bottom bars can collapse at different rates (each clamps to its own height), so tracking the top
 * fraction keeps the status bar in step with the chrome directly beneath it. On screens with no top
 * bar (topHeightLimit == 0) it falls back to the bottom bar, so the status bar still hides in sync
 * with the bottom navigation.
 *
 * Only the top status bar is touched; the bottom OS navigation bar is left alone.
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user swipe to peek the status bar while immersed.
 * The status bar is always restored on dispose so leaving an immersive screen can never strand a
 * hidden bar.
 */
@Composable
internal actual fun ImmersiveStatusBarEffect(state: DisappearingBarState) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = getActivityWindow() ?: return
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    LaunchedEffect(controller, state) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        var statusBarHidden = false
        snapshotFlow {
            if (state.topHeightLimit > 0f) state.topCollapsedFraction else state.bottomCollapsedFraction
        }.distinctUntilChanged()
            .collect { fraction ->
                // Hysteresis: hide once fully settled, but only show again after the chrome has
                // been pulled back past STATUS_BAR_SHOW_THRESHOLD. Between the two thresholds the
                // status bar keeps its current state, so a stray reverse reveal can't flip it.
                val shouldHide =
                    when {
                        fraction >= STATUS_BAR_HIDE_THRESHOLD -> true
                        fraction <= STATUS_BAR_SHOW_THRESHOLD -> false
                        else -> statusBarHidden
                    }
                if (shouldHide != statusBarHidden) {
                    statusBarHidden = shouldHide
                    if (shouldHide) {
                        controller.hide(WindowInsetsCompat.Type.statusBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
    }

    DisposableEffect(controller) {
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}
