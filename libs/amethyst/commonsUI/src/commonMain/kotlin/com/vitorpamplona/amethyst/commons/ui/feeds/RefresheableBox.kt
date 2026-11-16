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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.vitorpamplona.amethyst.commons.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RefresheableBox(
    invalidateableContent: InvalidatableContent,
    enablePullRefresh: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (enablePullRefresh) {
        RefresheableBox(onRefresh = invalidateableContent::invalidateData, content = content)
    } else {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefresheableBox(
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    val topPadding = LocalDisappearingScaffoldPadding.current.calculateTopPadding()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                onRefresh()
                delay(500)
                isRefreshing = false
            }
        },
        state = state,
        modifier = Modifier.fillMaxSize(),
        // Anchor the indicator below the scaffold's top bar. Inside a DisappearingScaffold
        // the content fills the full screen height (feed items scroll behind the bar),
        // so the default top-aligned indicator would sit behind the top bar. Outside a
        // scaffold the local default is 0.dp and behaviour is unchanged.
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding),
                isRefreshing = isRefreshing,
                state = state,
            )
        },
    ) {
        // Only a drag pulls the indicator. A mouse wheel or trackpad scrolling past the top would
        // pull it too, but Material resets the pull only when a drag is released (a fling), which a
        // wheel never sends: the indicator stayed half-pulled on screen and never refreshed.
        val wheelGuard = remember { WheelOverscrollGuard() }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(wheelGuard) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll) {
                                wheelGuard.wheel = true
                            } else if (event.type == PointerEventType.Press) {
                                wheelGuard.wheel = false
                            }
                        }
                    }
                }.nestedScroll(wheelGuard),
            content = content,
        )
    }
}

/** Keeps what a wheel scroll leaves over at the list's edge from reaching the pull indicator. */
private class WheelOverscrollGuard : NestedScrollConnection {
    var wheel = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (wheel) available else Offset.Zero
}
