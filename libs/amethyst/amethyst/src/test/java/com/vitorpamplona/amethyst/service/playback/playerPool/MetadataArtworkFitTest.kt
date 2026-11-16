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
package com.vitorpamplona.amethyst.service.playback.playerPool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The platform scales session artwork inside MediaMetadata.Builder.build() whenever a bitmap is
 * larger than config_mediaMetadataBitmapMaxSize, and media3 hands it the same Bitmap instance under
 * two keys — so that one instance is scaled twice, which crashes on ROMs that recycle the source.
 * These cases pin the sizes that keep build() from scaling at all.
 */
class MetadataArtworkFitTest {
    @Test
    fun bitmapsWithinTheLimitAreLeftAlone() {
        assertNull(fitArtworkWithin(320, 240, 960))
        // Exactly at the limit: the framework compares with >, so this must not be scaled either.
        assertNull(fitArtworkWithin(960, 960, 960))
        assertNull(fitArtworkWithin(960, 480, 960))
    }

    @Test
    fun anUnresolvedLimitScalesNothing() {
        assertNull(fitArtworkWithin(4000, 3000, 0))
        assertNull(fitArtworkWithin(4000, 3000, -1))
    }

    @Test
    fun squareArtworkIsCappedAtTheLimit() {
        assertEquals(ArtworkSize(960, 960), fitArtworkWithin(4000, 4000, 960))
    }

    @Test
    fun aspectRatioIsPreservedAndNeitherAxisExceedsTheLimit() {
        val landscape = fitArtworkWithin(4000, 2000, 960)!!
        assertEquals(ArtworkSize(960, 480), landscape)

        val portrait = fitArtworkWithin(2000, 4000, 960)!!
        assertEquals(ArtworkSize(480, 960), portrait)
    }

    @Test
    fun onlyOneOversizedAxisStillScalesBothDown() {
        // 1000x100 under a 960 cap: the framework scales by the smaller ratio, so the height goes
        // down with the width.
        assertEquals(ArtworkSize(960, 96), fitArtworkWithin(1000, 100, 960))
    }

    @Test
    fun extremeAspectRatiosNeverCollapseToZeroPixels() {
        // 10000x3 would truncate the height to 0 and Bitmap.createScaledBitmap would throw.
        val strip = fitArtworkWithin(10000, 3, 960)!!
        assertEquals(960, strip.width)
        assertEquals(1, strip.height)
    }

    @Test
    fun roundingNeverLandsOnePixelOverTheLimit() {
        // Float scale factors truncate, so the long edge lands on the limit or just under it —
        // never over, which is what would send the platform back into scaleBitmap().
        for (width in 961..4000) {
            val fitted = fitArtworkWithin(width, width - 1, 960)!!
            assert(fitted.width in 1..960) { "width ${fitted.width} out of range for $width" }
            assert(fitted.height in 1..960) { "height ${fitted.height} out of range for $width" }
        }
    }
}
