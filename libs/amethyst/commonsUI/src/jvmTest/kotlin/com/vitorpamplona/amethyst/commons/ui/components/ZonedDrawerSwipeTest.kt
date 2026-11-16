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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives [zonedDrawerSwipe] on a real two-page [HorizontalPager] through an offscreen
 * [ImageComposeScene], with touch drags and mouse-wheel scrolls. Density 1, so dp == px.
 */
class ZonedDrawerSwipeTest {
    private lateinit var scene: ImageComposeScene
    private var opened = 0
    private lateinit var pager: PagerState
    private var timeMs = 0L

    @AfterTest
    fun close() {
        if (::scene.isInitialized) scene.close()
    }

    private fun start(initialPage: Int) {
        scene =
            ImageComposeScene(width = 400, height = 800, density = Density(1f)) {
                pager = rememberPagerState(initialPage) { 2 }
                HorizontalPager(pager, Modifier.fillMaxSize().zonedDrawerSwipe(pager) { opened++ }) {
                    Box(Modifier.fillMaxSize())
                }
            }
        settle()
    }

    private fun frame() {
        scene.render(timeMs * 1_000_000)
    }

    private fun settle() {
        repeat(90) {
            timeMs += 16
            frame()
        }
    }

    private fun touch(
        type: PointerEventType,
        x: Float,
    ) = scene.sendPointerEvent(type, Offset(x, 400f), timeMillis = timeMs, type = PointerType.Touch)

    /** Drags right by [dx] in [steps] moves [stepMs] apart, then lifts. */
    private fun touchDrag(
        fromX: Float,
        dx: Float,
        steps: Int,
        stepMs: Long,
    ) {
        touch(PointerEventType.Press, fromX)
        for (i in 1..steps) {
            timeMs += stepMs
            touch(PointerEventType.Move, fromX + dx * i / steps)
            frame()
        }
        timeMs += stepMs
        touch(PointerEventType.Release, fromX + dx)
        settle()
    }

    /** Negative [dx] scrolls back towards the first page. */
    private fun wheel(
        dx: Float,
        times: Int,
    ) {
        repeat(times) {
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f, 400f), scrollDelta = Offset(dx, 0f), timeMillis = timeMs)
            settle()
        }
    }

    @Test
    fun aLongDragOnTheFirstPageOpensTheDrawer() {
        start(initialPage = 0)
        touchDrag(fromX = 100f, dx = 200f, steps = 20, stepMs = 100)
        assertEquals(1, opened)
    }

    @Test
    fun aShortSlowDragDoesNotOpenTheDrawer() {
        start(initialPage = 0)
        touchDrag(fromX = 100f, dx = 60f, steps = 6, stepMs = 100)
        assertEquals(0, opened)
    }

    @Test
    fun aShortFlickOpensTheDrawer() {
        start(initialPage = 0)
        touchDrag(fromX = 100f, dx = 60f, steps = 3, stepMs = 8)
        assertEquals(1, opened)
    }

    @Test
    fun aShortDragInTheDrawerZoneOfTheSecondPageKeepsThePage() {
        start(initialPage = 1)
        touchDrag(fromX = 300f, dx = 60f, steps = 6, stepMs = 100)
        assertEquals(0, opened)
        assertEquals(1, pager.currentPage)
    }

    @Test
    fun aDragInThePagerZoneOfTheSecondPageTurnsThePage() {
        start(initialPage = 1)
        touchDrag(fromX = 50f, dx = 250f, steps = 25, stepMs = 50)
        assertEquals(0, opened)
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun theWheelTurnsThePage() {
        start(initialPage = 1)
        wheel(dx = -10f, times = 1)
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun theWheelStillTurnsThePageAfterADrawerSwipe() {
        start(initialPage = 1)
        touchDrag(fromX = 300f, dx = 60f, steps = 6, stepMs = 100)
        wheel(dx = -10f, times = 1)
        assertEquals(0, pager.currentPage)
    }

    @Test
    fun theWheelAfterATapInTheDrawerZoneTurnsThePage() {
        start(initialPage = 1)
        touch(PointerEventType.Press, 300f)
        touch(PointerEventType.Release, 300f)
        settle()
        wheel(dx = -10f, times = 1)
        assertEquals(0, opened)
        assertEquals(0, pager.currentPage)
    }
}
