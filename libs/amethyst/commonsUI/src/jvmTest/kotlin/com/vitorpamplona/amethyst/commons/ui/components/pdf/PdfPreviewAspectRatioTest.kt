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
package com.vitorpamplona.amethyst.commons.ui.components.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The placeholder shown while a previously-seen PDF re-renders reserves its box from the ratio
 * cached in `MediaAspectRatioCache`, and the loaded thumbnail sizes itself from the ratio the
 * renderer reports. Those are the same number, so both sides must clamp it identically — a
 * placeholder that skipped the clamp would reserve a different box than the page lands in, which is
 * the jump it exists to remove.
 */
class PdfPreviewAspectRatioTest {
    @Test
    fun ordinaryPortraitPageIsUnchanged() {
        // US Letter, in PostScript points.
        assertEquals(612f / 792f, previewAspectRatio(612f / 792f), 0.0001f)
    }

    @Test
    fun landscapePageIsUnchanged() {
        assertEquals(792f / 612f, previewAspectRatio(792f / 612f), 0.0001f)
    }

    @Test
    fun pathologicallyTallPageIsClamped() {
        // A 200x4000 receipt would otherwise reserve 20 screens of height for a sliver of content.
        assertEquals(0.2f, previewAspectRatio(200f / 4000f), 0.0001f)
    }

    @Test
    fun theClampIsTheSameNumberBothSidesUse() {
        val cachedOnRevisit = 200f / 4000f
        val reportedByTheRenderer = 200f / 4000f

        assertEquals(previewAspectRatio(reportedByTheRenderer), previewAspectRatio(cachedOnRevisit), 0.0f)
    }

    /**
     * `Modifier.aspectRatio` requires a finite ratio greater than zero and throws otherwise, so
     * every one of these would crash the composition of the whole feed if it reached the modifier.
     * A page measuring 0x0 and an imeta `dim` of `"0.4x0.4"` (which truncates to 0x0, slipping past
     * the parser's literal-`"0x0"` rejection) both arrive here as 0/0.
     */
    @Test
    fun degenerateSizesNeverReachTheModifier() {
        assertUsable(previewAspectRatio(ratioOf(0, 0)))
        assertUsable(previewAspectRatio(ratioOf(100, 0)))
        assertUsable(previewAspectRatio(ratioOf(0, 100)))
        assertUsable(previewAspectRatio(-1f))
    }

    /**
     * Pins the reason the guard above cannot just be the clamp: every comparison against NaN is
     * false, so `coerceAtLeast` returns NaN unchanged rather than lifting it to the floor.
     */
    @Test
    fun clampingAloneDoesNotCatchNaN() {
        assertTrue(ratioOf(0, 0).coerceAtLeast(0.2f).isNaN())
    }

    @Test
    fun unknownSizedPagesFallBackToPortrait() {
        // Taller than wide, so an unknown page reserves a portrait box rather than a squat one.
        assertTrue(previewAspectRatio(ratioOf(0, 0)) < 1f)
    }

    // Computed rather than written as a literal so the compiler doesn't fold it into a
    // division-by-zero warning — these degenerate sizes are the point of the test.
    private fun ratioOf(
        width: Int,
        height: Int,
    ): Float = width.toFloat() / height.toFloat()

    private fun assertUsable(ratio: Float) {
        assertTrue(ratio.isFinite(), "$ratio is not finite")
        assertTrue(ratio > 0f, "$ratio is not > 0")
    }
}
