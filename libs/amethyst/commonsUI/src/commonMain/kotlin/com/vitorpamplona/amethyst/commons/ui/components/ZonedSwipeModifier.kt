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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

private const val PAGER_ZONE_FRACTION = 0.5f

// About a third of Material3's 360dp drawer, and Material3's own drawer fling threshold.
private val DRAWER_COMMIT_DISTANCE = 120.dp
private val DRAWER_COMMIT_VELOCITY = 400.dp

fun Modifier.zonedDrawerSwipe(
    pagerState: PagerState,
    openDrawer: () -> Unit,
): Modifier =
    composed {
        var widthPx by remember { mutableFloatStateOf(1f) }
        var gestureStartX by remember { mutableFloatStateOf(0f) }
        var gestureStartPage by remember { mutableIntStateOf(0) }

        // Wheel and trackpad scrolls reach the connection as UserInput too, but with no press
        // behind them; they belong to the pager, until the next press starts a swipe.
        var wheelScrolling by remember { mutableStateOf(false) }

        val density = LocalDensity.current
        val commit =
            remember(density) {
                with(density) { DrawerSwipeCommit(DRAWER_COMMIT_DISTANCE.toPx(), DRAWER_COMMIT_VELOCITY.toPx()) }
            }

        // The connection is remembered for the pager's lifetime; read the
        // current lambda through rememberUpdatedState so a caller that
        // re-creates openDrawer (new drawer state, account switch) is honoured.
        val currentOpenDrawer by rememberUpdatedState(openDrawer)
        val connection =
            remember(pagerState, commit) {
                object : NestedScrollConnection {
                    fun dragDrawer(dx: Float): Offset {
                        if (commit.drag(dx)) currentOpenDrawer()
                        return Offset(dx, 0f)
                    }

                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput || wheelScrolling) return Offset.Zero
                        if (commit.claimed) return dragDrawer(available.x)

                        // Non-first pages in the drawer zone: intercept before the
                        // pager consumes the delta to page backwards.
                        if (available.x > 0f && gestureStartPage != 0 && gestureStartX >= widthPx * PAGER_ZONE_FRACTION) {
                            return dragDrawer(available.x)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput || wheelScrolling) return Offset.Zero

                        // First page: claim the gesture only with unconsumed right-swipe
                        // so child LazyRows can scroll first.
                        if (available.x > 0f && gestureStartPage == 0) return dragDrawer(available.x)
                        return Offset.Zero
                    }

                    // Keep the release velocity from the pager too, so a flick that opens
                    // the drawer (or falls short of it) doesn't also turn the page.
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (!commit.claimed) return Velocity.Zero
                        if (commit.release(available.x)) currentOpenDrawer()
                        return Velocity(available.x, 0f)
                    }
                }
            }

        this
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(pagerState, commit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    gestureStartX = down.position.x
                    gestureStartPage = pagerState.currentPage
                    wheelScrolling = false
                    commit.reset()
                }
            }.pointerInput(commit) {
                // Initial pass: flag the scroll before the pager acts on it.
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Scroll) {
                            wheelScrolling = true
                            commit.reset()
                        }
                    }
                }
            }.nestedScroll(connection)
    }
