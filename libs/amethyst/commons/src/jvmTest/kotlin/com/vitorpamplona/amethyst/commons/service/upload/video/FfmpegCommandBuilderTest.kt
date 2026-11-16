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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegCommandBuilderTest {
    @Test
    fun portraitLimitsWidthAndKeepsEven() { // Review Focus 1
        assertEquals(
            "scale=trunc(iw*sar/2)*2:ih,setsar=1,scale='if(gt(iw,ih),-2,trunc(min(720,iw)/2)*2)':'if(gt(iw,ih),trunc(min(720,ih)/2)*2,-2)'",
            FfmpegCommandBuilder.scaleFilter(720),
        )
    }

    @Test
    fun noRuleOnlyEvensDimensions() {
        assertEquals("scale=trunc(iw*sar/2)*2:trunc(ih/2)*2,setsar=1", FfmpegCommandBuilder.scaleFilter(null))
    }

    @Test
    fun h264EncodeCommand() {
        val cmd = FfmpegCommandBuilder.encode("ffmpeg", File("in.mov"), File("out.mp4"), "libx264", 4_000_000, 1080)
        assertEquals(listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-map_chapters", "-1"), cmd.subList(cmd.indexOf("-map"), cmd.indexOf("-map") + 8))
        assertEquals(listOf("-c:v", "libx264", "-b:v", "4000000", "-maxrate", "4000000", "-bufsize", "8000000"), cmd.subList(cmd.indexOf("-c:v"), cmd.indexOf("-c:v") + 8))
        assertFalse("-tag:v" in cmd)
        assertTrue(cmd.containsAll(listOf("-movflags", "+faststart", "-map_metadata:s", "-progress", "pipe:1")))
        assertEquals("out.mp4", cmd.last())
    }

    @Test
    fun hevcEncodeTagsHvc1() {
        val cmd = FfmpegCommandBuilder.encode("ffmpeg", File("a"), File("b"), "hevc_videotoolbox", 1, null)
        assertEquals("hvc1", cmd[cmd.indexOf("-tag:v") + 1])
    }

    @Test
    fun remuxCopiesVideoAndAudioOnlyAndStrips() {
        val cmd = FfmpegCommandBuilder.remux("ffmpeg", File("a"), File("b"))
        assertEquals(
            listOf("-map", "0:v", "-map", "0:a?", "-dn", "-sn", "-c", "copy", "-map_metadata", "-1", "-map_metadata:s", "-1", "-map_chapters", "-1", "-movflags", "+faststart", "-progress", "pipe:1", "b"),
            cmd.subList(cmd.indexOf("-map"), cmd.size),
        )
        assertFalse("-c:v" in cmd)
        assertFalse("0" in cmd) // never `-map 0`
    }

    @Test
    fun inputIsPassedAsAnAbsolutePath() {
        // ffmpeg reads -i as a URL, so a relative name could name a protocol; the absolute path cannot.
        val relative = File("clip.mov")
        val encode = FfmpegCommandBuilder.encode("ffmpeg", relative, File("out.mp4"), "libx264", 1_000_000, null)
        val remux = FfmpegCommandBuilder.remux("ffmpeg", relative, File("out.mp4"))
        assertEquals(relative.absolutePath, encode[encode.indexOf("-i") + 1])
        assertEquals(relative.absolutePath, remux[remux.indexOf("-i") + 1])
    }
}
