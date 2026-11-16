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
package com.vitorpamplona.amethyst.commons.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A video player's report is what the picture looks like, so a `dim` the uploader got wrong (the
 * stored 1080x720 of a 480x720 anamorphic video) must neither overwrite it nor win over it.
 */
class MediaAspectRatioCacheTest {
    private val anamorphic = 480f / 720f

    @Test
    fun declaredDoesNotOverwriteMeasured() {
        val url = "https://example.com/declared-after.mp4"
        MediaAspectRatioCache.addMeasured(url, 480, 720)
        MediaAspectRatioCache.add(url, 1080, 720)
        assertEquals(anamorphic, MediaAspectRatioCache.get(url))
        assertEquals(anamorphic, MediaAspectRatioCache.measured(url))
    }

    @Test
    fun measuredReplacesDeclared() {
        val url = "https://example.com/declared-before.mp4"
        MediaAspectRatioCache.add(url, 1080, 720)
        assertNull(MediaAspectRatioCache.measured(url))
        MediaAspectRatioCache.addMeasuredRatio(url, anamorphic)
        assertEquals(anamorphic, MediaAspectRatioCache.get(url))
    }

    @Test
    fun videoRatioPrefersMeasuredThenDeclaredThenEstimate() {
        val url = "https://example.com/order.mp4"
        assertNull(MediaAspectRatioCache.videoRatio(url, null))
        MediaAspectRatioCache.add(url, 16, 9)
        assertEquals(16f / 9f, MediaAspectRatioCache.videoRatio(url, null))
        assertEquals(1.5f, MediaAspectRatioCache.videoRatio(url, 1.5f))
        MediaAspectRatioCache.addMeasured(url, 480, 720)
        assertEquals(anamorphic, MediaAspectRatioCache.videoRatio(url, 1.5f))
    }
}
