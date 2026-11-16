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
package com.vitorpamplona.amethyst.service.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastLivenessTest {
    @Test
    fun playerVerdictWinsOverMetadataWhenItSaysLive() {
        // A live .m3u8 shared in a plain kind:1 note carries no live-activity flag, so only
        // ExoPlayer's parsed verdict can save it from being cast as a seekable recording.
        assertTrue(resolveCastLiveness(learned = true, metadataFlag = false))
    }

    @Test
    fun playerVerdictWinsOverMetadataWhenItSaysOnDemand() {
        // A kind:30311 recording stays flagged live long after the broadcast ended; the parsed
        // playlist is the ground truth and must override it.
        assertFalse(resolveCastLiveness(learned = false, metadataFlag = true))
    }

    @Test
    fun fallsBackToMetadataWhileTheUrlIsStillUnclassified() {
        assertTrue(resolveCastLiveness(learned = null, metadataFlag = true))
        assertFalse(resolveCastLiveness(learned = null, metadataFlag = false))
    }

    @Test
    fun progressiveMediaStaysBufferedWhenNothingIsKnown() {
        // The common case: an MP4 with no verdict and no flag must keep its seek bar.
        assertFalse(resolveCastLiveness(learned = null, metadataFlag = false))
    }
}
