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

import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FfprobeOutputParserTest {
    private val json = """{"streams":[{"width":1920,"height":1080,"avg_frame_rate":"60000/1001","color_transfer":"bt709"}],"format":{"duration":"30.528000"}}"""

    @Test
    fun anamorphicStreamReportsItsDisplayWidth() {
        // 1080x720 stored with 4:9 pixels shows as 480x720 portrait.
        val r = FfprobeOutputParser.parse("""{"streams":[{"width":1080,"height":720,"sample_aspect_ratio":"4:9","avg_frame_rate":"30/1"}],"format":{}}""")!!
        assertEquals(VideoResolution(480, 720), r.info.resolution)
    }

    @Test
    fun unknownSampleAspectRatioKeepsTheWidth() {
        for (sar in listOf("0:1", "N/A", "1:1")) {
            val r = FfprobeOutputParser.parse("""{"streams":[{"width":1920,"height":1080,"sample_aspect_ratio":"$sar","avg_frame_rate":"30/1"}],"format":{}}""")!!
            assertEquals(VideoResolution(1920, 1080), r.info.resolution, sar)
        }
    }

    @Test
    fun parsesDimensionsFpsAndDuration() {
        val r = FfprobeOutputParser.parse(json)!!
        assertEquals(VideoResolution(1920, 1080), r.info.resolution)
        assertEquals(59.94f, r.info.framerate, 0.01f) // Review Focus 2
        assertEquals(30.528, r.durationSeconds!!, 0.001)
        assertFalse(r.isHdr)
    }

    @Test
    fun hdrTransfers() {
        for (t in listOf("smpte2084", "arib-std-b67")) {
            assertTrue(FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"30/1","color_transfer":"$t"}],"format":{}}""")!!.isHdr, t)
        }
    }

    @Test
    fun missingDurationAndTransferAreTolerated() {
        val r = FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"30/1"}],"format":{}}""")!!
        assertNull(r.durationSeconds)
        assertFalse(r.isHdr)
    }

    @Test
    fun noVideoStreamIsNull() {
        assertNull(FfprobeOutputParser.parse("""{"streams":[],"format":{"duration":"1"}}"""))
    }

    @Test
    fun garbageIsNull() {
        assertNull(FfprobeOutputParser.parse("not json"))
    }

    @Test
    fun zeroDenominatorFpsIsZero() {
        assertEquals(0f, FfprobeOutputParser.parse("""{"streams":[{"width":10,"height":10,"avg_frame_rate":"0/0"}],"format":{}}""")!!.info.framerate)
    }
}
