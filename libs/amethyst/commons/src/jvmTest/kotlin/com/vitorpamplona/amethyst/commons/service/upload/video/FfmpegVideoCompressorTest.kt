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
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FfmpegVideoCompressorTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("ffmpeg-compressor").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Scripted stand-in for ffmpeg: records commands, prints progress, writes the output file (last argument). */
    private class FakeRunner(
        val exitCodes: List<Int>,
        val outputBytes: Int = 0,
        val stderr: String = "",
        val progressLines: List<String> = emptyList(),
        val delayMs: Long = 0,
        val recordConcurrency: Boolean = false,
    ) : ProcessRunner {
        val commands = mutableListOf<List<String>>()
        var maxConcurrent = 0
        private var running = 0
        private var calls = 0

        override suspend fun run(
            command: List<String>,
            onStdoutLine: (String) -> Unit,
            stallTimeoutMs: Long,
        ): ProcessOutcome {
            commands += command
            val exit = exitCodes[calls++]
            if (recordConcurrency) {
                running++
                maxConcurrent = maxOf(maxConcurrent, running)
            }
            try {
                if (delayMs > 0) delay(delayMs)
                progressLines.forEach(onStdoutLine)
                File(command.last()).writeBytes(ByteArray(outputBytes))
            } finally {
                if (recordConcurrency) running--
            }
            return ProcessOutcome(exit, stderr)
        }
    }

    private fun compressor(
        runner: ProcessRunner,
        encoders: Set<String>,
        ffprobe: String? = "ffprobe",
        duration: Double? = 30.0,
        hdr: Boolean = false,
    ) = FfmpegVideoCompressor(
        locator = FfmpegLocator({ true }, { emptyList() }, { null }, disabled = false),
        encoders = { encoders },
        // A 1080p30 source unless `ffprobe` is "missing".
        probe = { if (ffprobe == null) null else ProbeResult(VideoInfo(VideoResolution(1920, 1080), 30f), duration, hdr) },
        runner = runner,
        gate = Semaphore(1),
        tempFile = { File.createTempFile("amethyst_video_", ".mp4", tempDir) },
    )

    private fun source(bytes: Int): File = File.createTempFile("src_", ".mov", tempDir).apply { writeBytes(ByteArray(bytes)) }

    private fun tempVideos(): List<File> = tempDir.listFiles().orEmpty().filter { it.name.startsWith("amethyst_video_") }

    private fun List<String>.after(flag: String) = this[indexOf(flag) + 1]

    @Test
    fun retriesNextEncoderWhenFirstFails() =
        runTest {
            // Review Focus 4
            val runner = FakeRunner(exitCodes = listOf(1, 0), outputBytes = 10)
            val r = compressor(runner, encoders = setOf("hevc_nvenc", "libx264")).compress(source(1000), CompressorQuality.HIGH, useH265 = true) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            assertEquals(listOf("hevc_nvenc", "libx264"), runner.commands.map { it.after("-c:v") })
            assertEquals(listOf("3000000", "4000000"), runner.commands.map { it.after("-b:v") }) // ×0.75 only for the HEVC attempt
            assertEquals(listOf(UploadNotice.HevcUnavailable), r.notices)
            assertEquals(1, tempVideos().size) // the failed temp is gone
        }

    @Test
    fun allEncodersFailingRemuxesOriginalWithNotice() =
        runTest {
            val runner = FakeRunner(exitCodes = listOf(1, 0), stderr = "boom", outputBytes = 10) // 2nd call is the remux
            val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
            assertEquals(listOf(UploadNotice.VideoCompressionFailed("boom")), (r as VideoCompressionResult.Original).notices)
            assertNotNull(r.stripped)
            assertTrue("copy" in runner.commands[1])
        }

    @Test
    fun largerOutputIsDiscardedAndOriginalRemuxed() =
        runTest {
            val runner = FakeRunner(exitCodes = listOf(0, 0), outputBytes = 5000)
            val r = compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
            assertIs<VideoCompressionResult.Original>(r)
            assertNotNull(r.stripped)
            assertEquals(listOf(UploadNotice.CompressionNotSmaller), r.notices)
            assertEquals(1, tempVideos().size)
        }

    @Test
    fun remuxFailureYieldsNullStripped() =
        runTest {
            val r = compressor(FakeRunner(listOf(0, 1), outputBytes = 5000), setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {}
            assertNull((r as VideoCompressionResult.Original).stripped)
            assertTrue(tempVideos().isEmpty())
        }

    @Test
    fun hdrSourceIsNoticed() =
        runTest {
            val r = compressor(FakeRunner(listOf(0), outputBytes = 10), setOf("libx264"), hdr = true).compress(source(1000), CompressorQuality.HIGH, false) {}
            assertEquals(listOf(UploadNotice.HdrSource), (r as VideoCompressionResult.Compressed).notices)
        }

    @Test
    fun missingFfprobeUsesProbeFailureFallback() =
        runTest {
            // Review Focus 3
            val runner = FakeRunner(listOf(0), outputBytes = 10)
            compressor(runner, setOf("libx264"), ffprobe = null).compress(source(1000), CompressorQuality.HIGH, false) {}
            val cmd = runner.commands.single()
            assertEquals("2000000", cmd.after("-b:v"))
            assertEquals("scale=trunc(iw*sar/2)*2:trunc(ih/2)*2,setsar=1", cmd.after("-vf"))
        }

    @Test
    fun probeExposesInfo() =
        runTest {
            assertEquals(VideoResolution(1920, 1080), compressor(FakeRunner(listOf()), setOf("libx264")).probe(source(1))!!.resolution)
            assertNull(compressor(FakeRunner(listOf()), setOf("libx264"), ffprobe = null).probe(source(1)))
        }

    @Test
    fun veryHighEncodesAsHigh() =
        runTest {
            val runner = FakeRunner(listOf(0), outputBytes = 10)
            compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.VERY_HIGH, false) {}
            assertEquals("4000000", runner.commands.single().after("-b:v"))
        }

    @Test
    fun progressIsForwarded() =
        runTest {
            val seen = mutableListOf<Double>()
            compressor(
                FakeRunner(listOf(0), outputBytes = 10, progressLines = listOf("out_time_us=1000000", "out_time_us=N/A", "progress=end")),
                setOf("libx264"),
                duration = 2.0,
            ).compress(source(1000), CompressorQuality.HIGH, false) { seen += it }
            assertEquals(listOf(0.5, 1.0), seen)
        }

    @Test
    fun gifKeepsSizeAndIgnoresSizeRule() =
        runTest {
            val runner = FakeRunner(listOf(0), outputBytes = 5000)
            val r = compressor(runner, setOf("libx264")).convertGif(source(1000)) {}
            assertIs<VideoCompressionResult.Compressed>(r)
            val cmd = runner.commands.single()
            assertEquals("scale=trunc(iw*sar/2)*2:trunc(ih/2)*2,setsar=1", cmd.after("-vf"))
            assertEquals("2000000", cmd.after("-b:v")) // MEDIUM row for 1080p
        }

    @Test
    fun gifFailureIsNeverRemuxed() =
        runTest {
            val runner = FakeRunner(listOf(1), stderr = "bad gif", outputBytes = 0)
            val r = compressor(runner, setOf("libx264")).convertGif(source(1000)) {}
            assertEquals(VideoCompressionResult.Original(null, listOf(UploadNotice.VideoCompressionFailed("bad gif"))), r)
            assertEquals(1, runner.commands.size) // no remux call
            assertTrue(tempVideos().isEmpty()) // no temp left
        }

    @Test
    fun encodesRunOneAtATime() =
        runTest {
            val runner = FakeRunner(listOf(0, 0), outputBytes = 10, delayMs = 50, recordConcurrency = true)
            val c = compressor(runner, setOf("libx264"))
            listOf(
                async { c.compress(source(1000), CompressorQuality.HIGH, false) {} },
                async { c.compress(source(1000), CompressorQuality.HIGH, false) {} },
            ).awaitAll()
            assertEquals(1, runner.maxConcurrent)
        }

    @Test
    fun cancellationDeletesTemp() =
        runTest {
            val runner = FakeRunner(listOf(0), outputBytes = 10, delayMs = 10_000)
            val job = launch { compressor(runner, setOf("libx264")).compress(source(1000), CompressorQuality.HIGH, false) {} }
            advanceTimeBy(100)
            job.cancelAndJoin()
            assertTrue(tempVideos().isEmpty())
        }

    @Test
    fun noFfmpegIsOriginalUnstripped() =
        runTest {
            val none = FfmpegVideoCompressor(locator = FfmpegLocator({ false }, { emptyList() }, { null }, disabled = false))
            assertEquals(VideoCompressionResult.Original(null), none.compress(source(1), CompressorQuality.HIGH, false) {})
        }

    @Test
    fun failedProbeIsNotCached() =
        runTest {
            var calls = 0
            val c =
                FfmpegVideoCompressor(
                    locator = FfmpegLocator({ true }, { emptyList() }, { null }, disabled = false),
                    encoders = { setOf("libx264") },
                    probe = { if (calls++ == 0) null else ProbeResult(VideoInfo(VideoResolution(1920, 1080), 30f), 30.0, false) },
                    runner = FakeRunner(listOf(0)),
                    gate = Semaphore(1),
                    tempFile = { File.createTempFile("amethyst_video_", ".mp4", tempDir) },
                )
            val src = source(1000)
            assertNull(c.probe(src))
            assertEquals(VideoResolution(1920, 1080), c.probe(src)?.resolution)
        }

    @Test
    fun remuxWaitsForTheEncodeGate() =
        runTest {
            val runner = FakeRunner(exitCodes = listOf(0, 0), outputBytes = 10, delayMs = 1_000, recordConcurrency = true)
            val c = compressor(runner, setOf("libx264"))
            listOf(
                async { c.compress(source(1000), CompressorQuality.HIGH, false) {} },
                async { c.strip(source(1000)) },
            ).awaitAll()
            assertEquals(1, runner.maxConcurrent)
        }
}
