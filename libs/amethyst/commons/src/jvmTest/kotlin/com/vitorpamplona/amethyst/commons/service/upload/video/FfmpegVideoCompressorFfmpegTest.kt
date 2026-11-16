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

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.intOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.longOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objectListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real encodes, gated on `ffmpeg`, `ffprobe` and `libx264` being present (the size and bitrate
 * figures below were measured with x264). Set `AMETHYST_FFMPEG_DISABLED=1` to run the suite as
 * a machine without ffmpeg; a bare `PATH` is not enough, the locator finds Homebrew by design.
 */
class FfmpegVideoCompressorFfmpegTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("ffmpeg-real").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun compressor() =
        FfmpegVideoCompressor(
            gate = Semaphore(1),
            tempFile = { File.createTempFile("amethyst_video_", ".mp4", tempDir) },
        )

    @Test
    fun highKeeps1080pUnderTheVbvBoundAndStripsTheTitle() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val r = compressor().compress(Fixtures.noisy, CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            val s = probe(r.file)
            assertEquals(1920 to 1080, s.width to s.height)
            assertTrue(s.bitRate <= 4_000_000 * (1 + 2 / 2.0), "bit_rate ${s.bitRate} within the VBV bound of a 2 s clip")
            assertNull(s.title)
        }

    @Test
    fun lowShrinksTo480pBelowHighsBitrate() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val low = compressor().compress(Fixtures.noisy, CompressorQuality.LOW, false) {}
            val high = compressor().compress(Fixtures.noisy, CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Compressed>(low)
            assertIs<VideoCompressionResult.Compressed>(high)
            val s = probe(low.file)
            assertEquals(480, minOf(s.width, s.height))
            assertTrue(s.bitRate < probe(high.file).bitRate)
        }

    @Test
    fun hevcIsUsedWhenAnEncoderWorks() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val r = compressor().compress(Fixtures.noisy, CompressorQuality.MEDIUM, true) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            if (UploadNotice.HevcUnavailable !in r.notices) assertEquals("hevc", probe(r.file).codec)
        }

    @Test
    fun easySourceIsKeptAsARemuxedOriginal() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val r = compressor().compress(Fixtures.easy, CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Original>(r)
            val stripped = assertNotNull(r.stripped)
            assertTrue(stripped.length() < Fixtures.easy.length() + 4096)
            assertNull(probe(stripped).title) // the source has one
        }

    @Test
    fun rotatedSourceComesOutPortraitWithoutSideData() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val r = compressor().compress(Fixtures.rotated, CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            val s = probe(r.file)
            assertEquals(1080 to 1920, s.width to s.height)
            assertFalse(s.hasSideData)
        }

    @Test
    fun anamorphicSourceComesOutInSquarePixelsAtItsDisplayShape() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            // 1080x720 stored with 4:9 pixels: a 480x720 portrait video.
            val r = compressor().compress(Fixtures.anamorphic, CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            val s = probe(r.file)
            assertEquals(480 to 720, s.width to s.height)
            assertEquals("1:1", s.sampleAspectRatio)
        }

    @Test
    fun stripDropsTheTimecodeTrackAndEveryTag() =
        runBlocking {
            if (!ffmpegForTests()) return@runBlocking
            val stripped = assertNotNull(compressor().strip(Fixtures.tc))
            val s = probe(stripped)
            assertEquals(listOf("video"), s.streamTypes)
            assertNull(s.title)
            assertNull(s.location)
        }

    private class Probed(
        val width: Int,
        val height: Int,
        val codec: String,
        val bitRate: Long,
        val hasSideData: Boolean,
        val sampleAspectRatio: String?,
        val streamTypes: List<String>,
        val title: String?,
        val location: String?,
    )

    private fun probe(file: File): Probed {
        val json =
            Fixtures.capture(
                FfmpegLocator.default.ffprobe!!,
                "-v",
                "error",
                "-show_entries",
                "stream=width,height,sample_aspect_ratio,codec_name,codec_type,bit_rate:stream_side_data=rotation:format_tags",
                "-of",
                "json",
                file.absolutePath,
            )
        val root = Json.parseToJsonElement(json).jsonObject
        val streams = root.objectListOrNull("streams").orEmpty()
        val video = streams.first { it.stringOrNull("codec_type") == "video" }
        val tags = root.objOrNull("format")?.objOrNull("tags")
        return Probed(
            width = video.intOrNull("width") ?: 0,
            height = video.intOrNull("height") ?: 0,
            codec = video.stringOrNull("codec_name") ?: "",
            bitRate = video.longOrNull("bit_rate") ?: 0L,
            hasSideData = "side_data_list" in video,
            sampleAspectRatio = video.stringOrNull("sample_aspect_ratio"),
            streamTypes = streams.map { it.stringOrNull("codec_type") ?: "" },
            title = tags?.stringOrNull("title"),
            location = tags?.stringOrNull("location"),
        )
    }

    private object Fixtures {
        private val dir: File by lazy {
            Files.createTempDirectory("ffmpeg-fixtures").toFile().also { d ->
                Runtime.getRuntime().addShutdownHook(Thread { d.deleteRecursively() })
            }
        }
        private val ffmpeg: String get() = FfmpegLocator.default.ffmpeg!!

        /** High entropy, ~90 MB, with a title tag: every encode is smaller, so the size rule keeps it. */
        val noisy: File by lazy {
            File(dir, "noisy.mp4").also {
                ffmpeg(
                    "-f",
                    "lavfi",
                    "-i",
                    "color=c=gray:s=1920x1080:r=30,noise=alls=100:allf=t+u",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine",
                    "-t",
                    "2",
                    "-shortest",
                    "-pix_fmt",
                    "yuv420p",
                    "-c:v",
                    "libx264",
                    "-crf",
                    "18",
                    "-preset",
                    "ultrafast",
                    "-metadata",
                    "title=secret",
                    it.absolutePath,
                )
            }
        }

        /** High entropy, anamorphic: 1080x720 stored with 4:9 pixels, shown as 480x720. */
        val anamorphic: File by lazy {
            File(dir, "anamorphic.mp4").also {
                ffmpeg(
                    "-f",
                    "lavfi",
                    "-i",
                    "color=c=gray:s=1080x720:r=30,noise=alls=100:allf=t+u,setsar=4/9",
                    "-t",
                    "2",
                    "-pix_fmt",
                    "yuv420p",
                    "-c:v",
                    "libx264",
                    "-crf",
                    "18",
                    "-preset",
                    "ultrafast",
                    it.absolutePath,
                )
            }
        }

        /** Low entropy, ~55 KB, with a title tag: every encode is larger, so the original is remuxed. */
        val easy: File by lazy {
            File(dir, "easy.mp4").also {
                ffmpeg(
                    "-f",
                    "lavfi",
                    "-i",
                    "testsrc=size=1920x1080:rate=30",
                    "-t",
                    "2",
                    "-pix_fmt",
                    "yuv420p",
                    "-metadata",
                    "title=secret",
                    it.absolutePath,
                )
            }
        }

        /** `noisy` with a 90° display rotation, as a phone writes it. */
        val rotated: File by lazy {
            File(dir, "rotated.mp4").also {
                ffmpeg("-display_rotation", "90", "-i", noisy.absolutePath, "-c", "copy", it.absolutePath)
            }
        }

        /** A MOV with a timecode data track plus location and title tags: `-map 0 -c copy` fails on it. */
        val tc: File by lazy {
            File(dir, "tc.mov").also {
                ffmpeg(
                    "-f",
                    "lavfi",
                    "-i",
                    "testsrc=size=1920x1080:rate=30",
                    "-t",
                    "2",
                    "-pix_fmt",
                    "yuv420p",
                    "-timecode",
                    "00:00:00:00",
                    "-metadata",
                    "location=+48.2082+016.3738/",
                    "-metadata",
                    "title=secret",
                    it.absolutePath,
                )
            }
        }

        /** Runs the real ffmpeg quietly and fails the fixture build on a non-zero exit. */
        private fun ffmpeg(vararg args: String) {
            val command = listOf(ffmpeg, "-hide_banner", "-loglevel", "error", "-y") + args
            val exit = ProcessBuilder(command).inheritIO().start().waitFor()
            check(exit == 0) { "ffmpeg exited $exit building ${command.last()}" }
        }

        fun capture(vararg command: String): String {
            val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val out = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "${command.first()} failed" }
            return out
        }
    }

    private companion object {
        val gate: Boolean by lazy {
            val ffmpeg = FfmpegLocator.default.ffmpeg
            ffmpeg != null && FfmpegLocator.default.ffprobe != null && "libx264" in runBlocking { FfmpegEncoders.listAvailable(ffmpeg) }
        }

        fun ffmpegForTests(): Boolean = gate.also { if (!it) println("ffmpeg, ffprobe or libx264 missing: skipping real-encode test") }
    }
}
