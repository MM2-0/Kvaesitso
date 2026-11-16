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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back_to_top
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The feed a screen's back-to-top button acts on. Every feed list under a disappearing scaffold
 * reports itself here (see [ReportToBackToTop]), and the one that last came on screen or was last
 * scrolled is the target, so a screen with tabs or a pager follows the page being read.
 */
@Stable
class BackToTopState(
    /** Runs after the feed is sent to top, for screens that also scroll something around it. */
    private val onSendToTop: () -> Unit = {},
) {
    private var active by mutableStateOf<BackToTopEntry?>(null)

    // Every list still composed, least recently shown or scrolled first.
    private val entries = mutableListOf<BackToTopEntry>()

    /** The button shows while the feed being read is scrolled well past its top. */
    val isVisible: Boolean get() = active?.isScrolledAway == true

    fun sendToTop() {
        active?.sendToTop?.invoke()
        onSendToTop()
    }

    internal fun activate(entry: BackToTopEntry) {
        entries.remove(entry)
        entries.add(entry)
        active = entry
    }

    /** A page swiped away hands the button back to the list that was in use before it. */
    internal fun remove(entry: BackToTopEntry) {
        entries.remove(entry)
        if (active === entry) active = entries.lastOrNull()
    }
}

internal class BackToTopEntry(
    val sendToTop: () -> Unit,
) {
    var isScrolledAway by mutableStateOf(false)
}

/** The back-to-top state of the scaffold around this content, or null where it has none. */
val LocalBackToTop = staticCompositionLocalOf<BackToTopState?> { null }

// About two screens of an average feed: past this, the back-to-top button shows.
private const val SCROLLED_AWAY_AFTER_ITEMS = 6

// Grid cells are a fraction of a row, so it takes more of them to scroll as far.
private const val SCROLLED_AWAY_AFTER_GRID_CELLS = 18

@Composable
fun ReportToBackToTop(
    listState: LazyListState,
    sendToTop: () -> Unit,
) = ReportToBackToTop(
    key = listState,
    isScrolledAway = { listState.firstVisibleItemIndex >= SCROLLED_AWAY_AFTER_ITEMS },
    isScrollInProgress = { listState.isScrollInProgress },
    sendToTop = sendToTop,
)

@Composable
fun ReportToBackToTop(
    gridState: LazyGridState,
    sendToTop: () -> Unit,
) = ReportToBackToTop(
    key = gridState,
    isScrolledAway = { gridState.firstVisibleItemIndex >= SCROLLED_AWAY_AFTER_GRID_CELLS },
    isScrollInProgress = { gridState.isScrollInProgress },
    sendToTop = sendToTop,
)

@Composable
fun ReportToBackToTop(
    gridState: LazyStaggeredGridState,
    sendToTop: () -> Unit,
) = ReportToBackToTop(
    key = gridState,
    isScrolledAway = { gridState.firstVisibleItemIndex >= SCROLLED_AWAY_AFTER_GRID_CELLS },
    isScrollInProgress = { gridState.isScrollInProgress },
    sendToTop = sendToTop,
)

@Composable
private fun ReportToBackToTop(
    key: Any,
    isScrolledAway: () -> Boolean,
    isScrollInProgress: () -> Boolean,
    sendToTop: () -> Unit,
) {
    val backToTop = LocalBackToTop.current ?: return
    val latestSendToTop by rememberUpdatedState(sendToTop)
    val entry = remember(backToTop, key) { BackToTopEntry { latestSendToTop() } }

    DisposableEffect(backToTop, entry) {
        backToTop.activate(entry)
        onDispose { backToTop.remove(entry) }
    }

    LaunchedEffect(backToTop, entry) {
        launch {
            snapshotFlow(isScrolledAway).distinctUntilChanged().collect { entry.isScrolledAway = it }
        }
        snapshotFlow(isScrollInProgress).filter { it }.collect { backToTop.activate(entry) }
    }
}

/**
 * The small button stacked above a screen's floating button. It lives in the scaffold's floating
 * slot, so it slides away with the bars while reading and comes back on the scroll up.
 */
@Composable
fun BackToTopButton(state: BackToTopState) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        SmallFloatingActionButton(
            onClick = state::sendToTop,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.loweredElevation(),
        ) {
            Icon(
                symbol = MaterialSymbols.ArrowUpward,
                contentDescription = stringRes(Res.string.back_to_top),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
