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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The padding the surrounding [DisappearingScaffold] would like its inner scrollable
 * to apply as `contentPadding`. Defaults to [PaddingValues] of 0 when no scaffold is
 * providing it, so feed composables used outside a scaffold behave as before.
 *
 * Read it via [rememberFeedContentPadding] to merge with the list's own
 * baseline padding (typically `FeedPadding`).
 */
val LocalDisappearingScaffoldPadding = compositionLocalOf { PaddingValues(0.dp) }

/**
 * The surrounding [DisappearingScaffold]'s bar state, exposed so inner content that swaps its
 * scrollable in place — and thereby resets the scroll position to the top without emitting a
 * scroll delta — can pull the bar back into view. Without this the bar can stay stuck at its
 * hidden offset over fresh top-of-list content, leaving a blank band. Null when no scaffold
 * provides it.
 */
val LocalDisappearingBarState = compositionLocalOf<DisappearingBarState?> { null }

/**
 * Extra start/end padding a host can ask feeds to apply so their content column stays at a
 * readable width while the scroll surface stays full-pane (scrolling and pull-to-refresh
 * keep working edge to edge). Feeds pick it up through [rememberFeedContentPadding].
 * Defaults to 0 everywhere.
 *
 * The Android shell does NOT provide this — it caps each NavHost destination's width
 * instead (CappedScreenContent), which also constrains top bars and non-feed screens.
 * The local remains for hosts that prefer padding-based capping (e.g. a desktop-style
 * reading column where gutters should still scroll).
 */
val LocalFeedSidePadding = compositionLocalOf { 0.dp }

/**
 * Merges two [PaddingValues] component-wise, resolving start/end against the current
 * [LocalLayoutDirection].
 */
@Composable
fun rememberMergedPadding(
    outer: PaddingValues,
    inner: PaddingValues,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return remember(outer, inner, layoutDirection) {
        PaddingValues(
            start = outer.calculateStartPadding(layoutDirection) + inner.calculateStartPadding(layoutDirection),
            top = outer.calculateTopPadding() + inner.calculateTopPadding(),
            end = outer.calculateEndPadding(layoutDirection) + inner.calculateEndPadding(layoutDirection),
            bottom = outer.calculateBottomPadding() + inner.calculateBottomPadding(),
        )
    }
}

/**
 * Convenience for inner LazyColumns/LazyVerticalGrids inside a [DisappearingScaffold]:
 * merges the scaffold's reserved space with the list's own baseline padding, plus the
 * [LocalFeedSidePadding] width cap requested by wide layouts — all folded into a single
 * remember slot, since this runs in every feed on every recomposition.
 */
@Composable
fun rememberFeedContentPadding(inner: PaddingValues): PaddingValues {
    val outer = LocalDisappearingScaffoldPadding.current
    val sidePadding = LocalFeedSidePadding.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(outer, inner, sidePadding, layoutDirection) {
        PaddingValues(
            start = outer.calculateStartPadding(layoutDirection) + inner.calculateStartPadding(layoutDirection) + sidePadding,
            top = outer.calculateTopPadding() + inner.calculateTopPadding(),
            end = outer.calculateEndPadding(layoutDirection) + inner.calculateEndPadding(layoutDirection) + sidePadding,
            bottom = outer.calculateBottomPadding() + inner.calculateBottomPadding(),
        )
    }
}
