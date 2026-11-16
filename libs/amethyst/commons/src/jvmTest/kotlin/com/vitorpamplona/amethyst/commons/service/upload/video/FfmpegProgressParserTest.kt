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
package com.vitorpamplona.amethyst.commons.service.upload.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FfmpegProgressParserTest {
    @Test
    fun fractionOfDuration() {
        assertEquals(0.5, FfmpegProgressParser(10.0).onLine("out_time_us=5000000")!!, 1e-9)
    }

    @Test
    fun clampsOvershoot() {
        assertEquals(1.0, FfmpegProgressParser(10.0).onLine("out_time_us=99000000"))
    }

    @Test
    fun endIsOne() {
        assertEquals(1.0, FfmpegProgressParser(null).onLine("progress=end"))
    }

    @Test
    fun notAvailableAndOtherKeysAreNull() {
        val p = FfmpegProgressParser(10.0)
        assertNull(p.onLine("out_time_us=N/A"))
        assertNull(p.onLine("frame=12"))
        assertNull(FfmpegProgressParser(null).onLine("out_time_us=5"))
    }
}
