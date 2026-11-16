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
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The probe against stand-in `ffprobe` scripts, so a hung or failing binary is covered without a real one. */
class FfmpegProbeTest {
    private val isWindows = FfmpegTestSupport.isWindows

    private fun script(body: String): File = FfmpegTestSupport.fakeBinary(body)

    @Test
    fun hungProbeIsKilledAndNull() =
        runBlocking {
            if (isWindows) return@runBlocking
            val started = System.currentTimeMillis()
            assertNull(FfmpegProbe(script("sleep 30").absolutePath, stallTimeoutMs = 200).probe(File("whatever.mov")))
            assertTrue(System.currentTimeMillis() - started < 5_000, "killed by the stall watchdog")
            assertTrue(FfmpegProcesses.live.isEmpty())
        }

    @Test
    fun outputOfTheBinaryIsParsed() =
        runBlocking {
            if (isWindows) return@runBlocking
            val json = """{"streams":[{"width":10,"height":20,"avg_frame_rate":"30/1"}],"format":{"duration":"1.5"}}"""
            val r = FfmpegProbe(script("echo '$json'").absolutePath).probe(File("whatever.mov"))!!
            assertEquals(VideoResolution(10, 20), r.info.resolution)
            assertEquals(1.5, r.durationSeconds)
        }

    @Test
    fun failingBinaryIsNull() =
        runBlocking {
            if (isWindows) return@runBlocking
            assertNull(FfmpegProbe(script("exit 1").absolutePath).probe(File("whatever.mov")))
        }
}
