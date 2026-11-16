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

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegEncodersTest {
    /** Real `ffmpeg -hide_banner -encoders` shape: a legend, a separator, then six flag characters, name, description. */
    private val gpl =
        """
        Encoders:
         V..... = Video
         ------
         V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)
         V....D h264_videotoolbox    VideoToolbox H.264 Encoder (codec h264)
         V....D libx265              libx265 H.265 / HEVC (codec hevc)
         V....D hevc_nvenc           NVIDIA NVENC hevc encoder (codec hevc)
         V....D h264_vaapi           H.264/AVC (VAAPI) (codec h264)
         A....D aac                  AAC (Advanced Audio Coding)
        """.trimIndent()

    private val lgpl =
        gpl.lines().filterNot { "libx26" in it }.joinToString("\n") +
            "\n V....D libopenh264          OpenH264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)"

    @Test
    fun parsesNamesOnly() {
        assertEquals(setOf("libx264", "h264_videotoolbox", "libx265", "hevc_nvenc", "h264_vaapi", "aac"), FfmpegEncoders.parseAvailable(gpl))
    }

    @Test
    fun vaapiIsNeverACandidate() {
        assertFalse("h264_vaapi" in FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(gpl), false))
    }

    @Test
    fun gplBuildPrefersX264() {
        assertEquals(listOf("libx264", "h264_videotoolbox"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(gpl), useH265 = false))
    }

    @Test
    fun lgplBuildFallsBackThroughHardwareToOpenH264() {
        assertEquals(listOf("h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(lgpl), useH265 = false))
    }

    @Test
    fun hevcCandidatesComeFirstThenH264() { // Review Focus 4
        assertEquals(listOf("libx265", "hevc_nvenc", "libx264", "h264_videotoolbox"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(gpl), useH265 = true))
        assertEquals(listOf("hevc_nvenc", "h264_videotoolbox", "libopenh264"), FfmpegEncoders.candidates(FfmpegEncoders.parseAvailable(lgpl), useH265 = true))
    }

    @Test
    fun hungListingIsKilledAndNotCached() =
        runBlocking {
            if (FfmpegTestSupport.isWindows) return@runBlocking
            val script = FfmpegTestSupport.fakeBinary("sleep 30")
            val started = System.currentTimeMillis()
            assertEquals(emptySet(), FfmpegEncoders.listAvailable(script.absolutePath, stallTimeoutMs = 200))
            assertTrue(System.currentTimeMillis() - started < 5_000, "killed by the stall watchdog")
            assertTrue(FfmpegProcesses.live.isEmpty())
        }

    @Test
    fun isHevc() {
        assertTrue(FfmpegEncoders.isHevc("hevc_videotoolbox"))
        assertFalse(FfmpegEncoders.isHevc("libx264"))
    }
}
