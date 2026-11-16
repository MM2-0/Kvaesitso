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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPostKindTest {
    private val portrait = DimensionTag(1080, 1920)
    private val landscape = DimensionTag(1920, 1080)
    private val square = DimensionTag(1080, 1080)

    @Test
    fun autoFollowsTheOrientation() {
        assertTrue(VideoPostKind.AUTO.isShort(portrait))
        assertFalse(VideoPostKind.AUTO.isShort(landscape))
        // A square video is not taller than it is wide, so it stays a normal video.
        assertFalse(VideoPostKind.AUTO.isShort(square))
    }

    @Test
    fun shortStaysShortEvenWhenTheFootageIsLandscape() {
        // The Shorts feed only reads kind 22. A landscape video shared to the "New Short" target
        // (or recorded from the Shorts composer) has to be a short or it never shows up there.
        assertTrue(VideoPostKind.SHORT.isShort(landscape))
        assertTrue(VideoPostKind.SHORT.isShort(portrait))
        assertTrue(VideoPostKind.SHORT.isShort(square))
    }

    @Test
    fun normalStaysNormalEvenWhenTheFootageIsPortrait() {
        assertFalse(VideoPostKind.NORMAL.isShort(portrait))
        assertFalse(VideoPostKind.NORMAL.isShort(landscape))
        assertFalse(VideoPostKind.NORMAL.isShort(square))
    }
}
