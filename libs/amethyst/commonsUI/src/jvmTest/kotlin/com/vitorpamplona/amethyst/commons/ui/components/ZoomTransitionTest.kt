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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The full-screen viewer's transition, headless: a red "photo" filling a 200x200 viewer opens out of
 * a thumbnail at (10,10)-(60,60) and shrinks back into it on dismiss.
 */
class ZoomTransitionTest {
    private val thumbnail = Rect(10f, 10f, 60f, 60f)

    private fun Image.rgb(
        x: Int,
        y: Int,
    ): Triple<Int, Int, Int> {
        val color = Bitmap.makeFromImage(this).getColor(x, y)
        return Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
    }

    private fun Image.isRed(
        x: Int,
        y: Int,
    ): Boolean {
        val (r, g, b) = rgb(x, y)
        return r > 200 && g < 60 && b < 60
    }

    private fun Image.isBlue(
        x: Int,
        y: Int,
    ): Boolean {
        val (r, g, b) = rgb(x, y)
        return b > 200 && r < 60 && g < 60
    }

    @Test
    fun growsOutOfTheThumbnailAndShrinksBackIntoIt() {
        var transition: ZoomTransition? = null
        var dismissed = false
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                val t = rememberZoomTransition({ ZoomSource(thumbnail) }, onDismissed = { dismissed = true })
                transition = t
                Box(Modifier.fillMaxSize().zoomTransitionContainer(t)) {
                    Box(Modifier.fillMaxSize().zoomTransitionLayer(t)) {
                        Box(Modifier.fillMaxSize().background(Color.Red).onGloballyPositioned { t.onMediaPositioned(it, null) })
                    }
                }
            }
        try {
            val ms = 1_000_000L
            scene.render(0)
            scene.render(16 * ms)
            val start = scene.render(32 * ms)
            assertTrue(start.isRed(35, 35), "the photo starts inside the thumbnail")
            assertFalse(start.isRed(150, 150), "and nowhere else")

            val open = scene.render(600 * ms)
            assertEquals(1f, transition!!.progress)
            assertTrue(open.isRed(150, 150), "then fills the viewer")

            transition.dismiss()
            scene.render(616 * ms)
            scene.render(632 * ms)
            val closing = scene.render(760 * ms)
            assertFalse(closing.isRed(190, 190), "on dismiss it pulls back toward the thumbnail")
            assertFalse(dismissed, "the viewer stays until the exit has played")

            scene.render(1200 * ms)
            scene.render(1216 * ms)
            assertTrue(dismissed, "and closes once it has")
        } finally {
            scene.close()
        }
    }

    /**
     * A thumbnail scrolled halfway out of the top of the feed: only its bottom half, (10,35)-(60,60),
     * was on screen, showing the bottom (blue) half of a red-over-blue photo. The transition must
     * start from and end on that, not on the middle of the photo squeezed into the visible slice.
     */
    @Test
    fun aThumbnailCutOffByTheFeedOpensFromWhatWasOnScreen() {
        var transition: ZoomTransition? = null
        val source = ZoomSource(bounds = thumbnail, visibleBounds = Rect(10f, 35f, 60f, 60f))
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                val t = rememberZoomTransition({ source }, onDismissed = {})
                transition = t
                Box(Modifier.fillMaxSize().zoomTransitionContainer(t)) {
                    Box(Modifier.fillMaxSize().zoomTransitionLayer(t)) {
                        Column(Modifier.fillMaxSize().onGloballyPositioned { t.onMediaPositioned(it, null) }) {
                            Box(Modifier.fillMaxSize().weight(1f).background(Color.Red))
                            Box(Modifier.fillMaxSize().weight(1f).background(Color.Blue))
                        }
                    }
                }
            }
        try {
            val ms = 1_000_000L
            scene.render(0)
            scene.render(16 * ms)
            val start = scene.render(32 * ms)
            assertTrue(start.isBlue(35, 38), "the visible slice shows the bottom half of the photo, as the feed did")
            assertTrue(start.isBlue(35, 58), "all the way down")
            assertFalse(start.isRed(35, 30), "nothing shows where the feed had cut the thumbnail off")

            scene.render(600 * ms)
            assertEquals(1f, transition!!.progress)

            // Pause the exit just before it ends: it must already be on the feed's slice.
            transition.dismiss()
            scene.render(616 * ms)
            scene.render(632 * ms)
            val closing = scene.render(860 * ms)
            assertTrue(closing.isBlue(35, 40), "the exit lands on the bottom half of the photo")
            assertFalse(closing.isRed(35, 40), "not on its middle")
        } finally {
            scene.close()
        }
    }

    @Test
    fun zoomSourceKeepsTheWholeRectOfAClippedLayout() {
        var source: ZoomSource? = null
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                Box(Modifier.offset(20.dp, 20.dp).size(100.dp).clipToBounds()) {
                    Box(Modifier.offset(10.dp, (-25).dp).size(50.dp).onGloballyPositioned { source = it.zoomSource() })
                }
            }
        try {
            scene.render(0)
            assertEquals(Rect(30f, -5f, 80f, 45f), source!!.bounds)
            assertEquals(Rect(30f, 20f, 80f, 45f), source.visibleBounds)
        } finally {
            scene.close()
        }
    }

    @Test
    fun zoomSourceIsCutToTheViewport() {
        var source: ZoomSource? = null
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.offset(30.dp, 10.dp).size(50.dp).onGloballyPositioned { source = it.zoomSource(Rect(0f, 40f, 200f, 200f)) })
                }
            }
        try {
            scene.render(0)
            assertEquals(Rect(30f, 10f, 80f, 60f), source!!.bounds)
            assertEquals(Rect(30f, 40f, 80f, 60f), source.visibleBounds, "the part above the viewport was under a bar")
        } finally {
            scene.close()
        }
    }

    /**
     * The scaffold lays its content out under its top bar, so nothing clips a thumbnail scrolled
     * under the bar: the viewport it provides must leave the bar out.
     */
    @Test
    fun aThumbnailUnderTheScaffoldsTopBarIsCutAtTheBar() {
        var anchor: ZoomSourceAnchor? = null
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                DisappearingScaffold(
                    isInvertedLayout = false,
                    topBar = { Box(Modifier.fillMaxWidth().height(40.dp)) },
                    allowBarHide = false,
                ) {
                    val a = rememberZoomSourceAnchor()
                    anchor = a
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .padding(top = 20.dp)
                                .offset(10.dp, 0.dp)
                                .size(50.dp)
                                .zoomSourceAnchor(a),
                        )
                    }
                }
            }
        try {
            scene.render(0)
            val source = anchor!!.capture()!!
            assertEquals(Rect(10f, 20f, 60f, 70f), source.bounds)
            // The bar is 40 tall plus its divider.
            assertTrue(source.visibleBounds.top >= 40f, "the slice under the top bar is left out: ${source.visibleBounds}")
            assertEquals(70f, source.visibleBounds.bottom)
        } finally {
            scene.close()
        }
    }

    @Test
    fun theExitShrinksIntoWhereTheThumbnailIsWhenItCloses() {
        var transition: ZoomTransition? = null
        var current = ZoomSource(thumbnail)
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                val t = rememberZoomTransition({ current }, onDismissed = {})
                transition = t
                Box(Modifier.fillMaxSize().zoomTransitionContainer(t)) {
                    Box(Modifier.fillMaxSize().zoomTransitionLayer(t)) {
                        Box(Modifier.fillMaxSize().background(Color.Red).onGloballyPositioned { t.onMediaPositioned(it, null) })
                    }
                }
            }
        try {
            val ms = 1_000_000L
            scene.render(0)
            scene.render(600 * ms)
            assertEquals(thumbnail, transition!!.sourceBounds!!.bounds)

            val moved = Rect(110f, 110f, 160f, 160f)
            current = ZoomSource(moved)
            transition.dismiss()
            assertEquals(moved, transition.sourceBounds!!.bounds)

            scene.render(616 * ms)
            scene.render(632 * ms)
            val closing = scene.render(860 * ms)
            assertTrue(closing.isRed(135, 135), "it lands where the thumbnail is now")
            assertFalse(closing.isRed(35, 35), "not where it was")
        } finally {
            scene.close()
        }
    }

    /**
     * A viewer opened from thumbnail "a" and paged to "b": it closes into b's thumbnail, and fades
     * for media the note doesn't show, instead of flying b into a's place.
     */
    @Test
    fun aPagedViewerClosesIntoTheThumbnailOfTheMediaOnScreen() {
        var group: ZoomSourceGroup? = null
        var a: ZoomSourceAnchor? = null
        var showB by mutableStateOf(true)
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                ProvideZoomSourceGroup {
                    group = LocalZoomSourceGroup.current
                    Box(Modifier.fillMaxSize()) {
                        val anchorA = rememberZoomSourceAnchor("a")
                        a = anchorA
                        Box(Modifier.offset(10.dp, 10.dp).size(50.dp).zoomSourceAnchor(anchorA))
                        if (showB) {
                            val anchorB = rememberZoomSourceAnchor("b")
                            Box(Modifier.offset(110.dp, 110.dp).size(50.dp).zoomSourceAnchor(anchorB))
                        }
                    }
                }
            }
        try {
            scene.render(0)
            assertEquals(thumbnail, zoomSourceFor("a", "a", a, group)!!.bounds)
            assertEquals(Rect(110f, 110f, 160f, 160f), zoomSourceFor("b", "a", a, group)!!.bounds)
            assertEquals(ZoomSource.Offscreen, zoomSourceFor("c", "a", a, group), "not in the note: fade")
            assertEquals(ZoomSource.Offscreen, zoomSourceFor("b", "a", a, null), "no group: fade")

            showB = false
            scene.render(16_000_000L)
            assertEquals(ZoomSource.Offscreen, zoomSourceFor("b", "a", a, group), "b's thumbnail left the screen")
        } finally {
            scene.close()
        }
    }

    @Test
    fun closingOnMediaWithNoThumbnailFadesInPlace() {
        var transition: ZoomTransition? = null
        var current = ZoomSource(thumbnail)
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                val t = rememberZoomTransition({ current }, onDismissed = {})
                transition = t
                Box(Modifier.fillMaxSize().zoomTransitionContainer(t)) {
                    Box(Modifier.fillMaxSize().zoomTransitionLayer(t)) {
                        Box(Modifier.fillMaxSize().background(Color.Red).onGloballyPositioned { t.onMediaPositioned(it, null) })
                    }
                }
            }
        try {
            val ms = 1_000_000L
            scene.render(0)
            scene.render(16 * ms)
            scene.render(32 * ms)
            scene.render(600 * ms)
            assertEquals(1f, transition!!.progress)

            current = ZoomSource.Offscreen
            transition.dismiss()
            assertEquals(ZoomSource.Offscreen, transition.sourceBounds)

            scene.render(616 * ms)
            scene.render(632 * ms)
            val closing = Bitmap.makeFromImage(scene.render(740 * ms))
            val corner = closing.getColor(190, 190)
            val alpha = corner ushr 24
            assertTrue(alpha in 1..254 && (corner shr 16) and 0xFF > 200, "it stays full size, half faded: ${Integer.toHexString(corner)}")
            assertEquals(corner, closing.getColor(35, 35), "and does not shrink into the old thumbnail")
        } finally {
            scene.close()
        }
    }
}
