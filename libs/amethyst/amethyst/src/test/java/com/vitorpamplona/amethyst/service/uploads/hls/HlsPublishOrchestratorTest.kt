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
package com.vitorpamplona.amethyst.service.uploads.hls

import com.davotoula.lightcompressor.Resolution
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsLadder
import com.davotoula.lightcompressor.hls.HlsRenditionSummary
import com.davotoula.lightcompressor.hls.HlsUploadResult
import com.davotoula.lightcompressor.hls.HlsUploaded
import com.davotoula.lightcompressor.hls.Rendition
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.upload.HlsPublishState
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPosterUpload
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPublishOrchestrator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.HlsPublishRequest
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HlsPublishOrchestratorTest {
    private lateinit var workDir: File

    private val server = ServerName("Test Blossom", "https://test.example/", ServerType.Blossom)

    @Before
    fun setUp() {
        workDir = Files.createTempDirectory("hls-orchestrator-test").toFile()
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
    }

    private fun landscapeSummary(
        resolution: Resolution = Resolution.SD_360,
        width: Int = 640,
        height: Int = 360,
    ): HlsRenditionSummary =
        HlsRenditionSummary(
            rendition = Rendition(resolution, bitrateKbps = 500),
            mediaPlaylist = "",
            playlistFilename = "${resolution.label}/media.m3u8",
            width = width,
            height = height,
            codecString = "avc1.64001f",
            combinedFilename = "${resolution.label}.mp4",
        )

    /**
     * Simulates an HlsUploadHelper run: it calls [uploadFile] once per segment (combined .mp4)
     * and once per media playlist, in the same order the real helper would, collects every
     * returned [HlsUploaded] into the `uploads` map the real helper surfaces, and returns a
     * fake [HlsUploadResult] with the supplied rendition summaries.
     */
    private fun fakeRunUpload(renditions: List<HlsRenditionSummary> = listOf(landscapeSummary())): suspend (
        config: com.davotoula.lightcompressor.hls.HlsConfig,
        listener: com.davotoula.lightcompressor.hls.HlsListener,
        uploadFile: suspend (File, String) -> HlsUploaded<MediaUploadResult>,
    ) -> HlsUploadResult<MediaUploadResult> =
        { _, listener, uploadFile ->
            val uploads = linkedMapOf<String, HlsUploaded<MediaUploadResult>>()
            listener.onStart(renditions.size)
            for (summary in renditions) {
                val combinedFilename =
                    requireNotNull(summary.combinedFilename) { "test fixtures must use single-file mode" }
                listener.onRenditionStart(summary.rendition)
                listener.onProgress(summary.rendition, 50f)
                val combinedFile = File(workDir, combinedFilename).apply { writeText("combined-${summary.rendition.resolution.label}") }
                uploads[combinedFilename] = uploadFile(combinedFile, combinedFilename)
                val playlistFile =
                    File(workDir, "${summary.rendition.resolution.label}-media.m3u8").apply { writeText("playlist-${summary.rendition.resolution.label}") }
                uploads[summary.playlistFilename] = uploadFile(playlistFile, summary.playlistFilename)
            }
            listener.onComplete("#EXTM3U\nfake-master-playlist")
            HlsUploadResult(
                masterPlaylist = "#EXTM3U\nrewritten-master-playlist",
                renditions = renditions,
                uploads = uploads,
            )
        }

    private class CannedUploader : HlsBlobUploader {
        var count = 0

        override suspend fun upload(
            file: File,
            contentType: String,
            onProgress: (Long, Long) -> Unit,
        ): MediaUploadResult {
            count++
            // Emit a single end-of-file progress tick so tests can verify the callback is
            // threaded through without coupling to throttling behaviour.
            val length = file.length()
            onProgress(length, length)
            return MediaUploadResult(url = "https://cdn.test/$count", sha256 = "sha-$count", size = length)
        }
    }

    private fun fakeUploadMaster(uploader: HlsBlobUploader): suspend (HlsBlobUploader, String) -> MediaUploadResult =
        { _, masterPlaylist ->
            val tmp = File(workDir, "master-${System.nanoTime()}.m3u8").apply { writeText(masterPlaylist) }
            try {
                uploader.upload(tmp, "application/vnd.apple.mpegurl") { _, _ -> }
            } finally {
                tmp.delete()
            }
        }

    private fun newRequest(
        title: String = "My HD Clip",
        description: String = "A test clip",
        sensitive: Boolean = false,
        warningReason: String = "",
        ladder: HlsLadder = HlsLadder(listOf(Rendition(Resolution.SD_360, 500))),
    ) = HlsPublishRequest(
        title = title,
        description = description,
        sensitiveContent = sensitive,
        contentWarningReason = warningReason,
        codec = VideoCodec.H265,
        server = server,
        ladder = ladder,
    )

    @Test
    fun happyPathEndsInSuccessWithMasterUrlAndEventId() {
        val publishedTemplates = mutableListOf<HlsVideoEventTemplate>()
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { tpl, _ ->
                    publishedTemplates += tpl
                    "signed-event-id"
                },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue("expected Success, was $final", final is HlsPublishState.Success)
        final as HlsPublishState.Success
        assertEquals("signed-event-id", final.eventId)
        assertTrue("masterUrl should contain https://cdn.test/", final.masterUrl.startsWith("https://cdn.test/"))

        assertEquals(1, publishedTemplates.size)
        assertTrue(publishedTemplates[0] is HlsVideoEventTemplate.Horizontal)
    }

    @Test
    fun statePhasesVisibleToFakesDuringPublish() {
        // Capture state.value at the point each phase's fake runs — this verifies that the
        // orchestrator has already transitioned into the right state before dispatching the
        // corresponding dep call.
        lateinit var orchestrator: HlsPublishOrchestrator
        val capturedDuringTranscode = mutableListOf<HlsPublishState>()
        val capturedDuringUpload = mutableListOf<HlsPublishState>()
        val capturedDuringPublish = mutableListOf<HlsPublishState>()

        val canned = CannedUploader()
        val capturingRunUpload: suspend (
            com.davotoula.lightcompressor.hls.HlsConfig,
            com.davotoula.lightcompressor.hls.HlsListener,
            suspend (File, String) -> HlsUploaded<MediaUploadResult>,
        ) -> HlsUploadResult<MediaUploadResult> = { _, listener, uploadFile ->
            val summary = landscapeSummary()
            val uploads = linkedMapOf<String, HlsUploaded<MediaUploadResult>>()
            listener.onStart(1)
            listener.onRenditionStart(summary.rendition)
            capturedDuringTranscode += orchestrator.state.value
            listener.onProgress(summary.rendition, 42f)
            capturedDuringTranscode += orchestrator.state.value
            val combinedFile = File(workDir, "360p.mp4").apply { writeText("bytes") }
            uploads["360p.mp4"] = uploadFile(combinedFile, "360p.mp4")
            capturedDuringUpload += orchestrator.state.value
            val playlistFile = File(workDir, "360p-media.m3u8").apply { writeText("bytes") }
            uploads["360p/media.m3u8"] = uploadFile(playlistFile, "360p/media.m3u8")
            listener.onComplete("#EXTM3U\nmaster")
            HlsUploadResult(
                masterPlaylist = "#EXTM3U\nrewritten",
                renditions = listOf(summary),
                uploads = uploads,
            )
        }

        orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = capturingRunUpload,
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { _, _ ->
                    capturedDuringPublish += orchestrator.state.value
                    "event-id"
                },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        assertTrue(capturedDuringTranscode.all { it is HlsPublishState.Transcoding })
        assertEquals("360p", (capturedDuringTranscode.last() as HlsPublishState.Transcoding).currentLabel)
        assertEquals(42, (capturedDuringTranscode.last() as HlsPublishState.Transcoding).percent)

        assertTrue(capturedDuringUpload.single() is HlsPublishState.Uploading)
        assertTrue(capturedDuringPublish.single() is HlsPublishState.Publishing)

        assertTrue(orchestrator.state.value is HlsPublishState.Success)
    }

    @Test
    fun transcodeExceptionTransitionsToFailure() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = { _, _, _ -> throw RuntimeException("decode failed") },
                buildUploader = { CannedUploader() },
                uploadMaster = { _, _ -> MediaUploadResult(url = "never") },
                signAndPublish = { _, _ -> "never" },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue("expected Failure, was $final", final is HlsPublishState.Failure)
        assertEquals("decode failed", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun uploadExceptionTransitionsToFailure() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = {
                    HlsBlobUploader { _, _, _ -> throw RuntimeException("server 500") }
                },
                uploadMaster = { _, _ -> MediaUploadResult(url = "never") },
                signAndPublish = { _, _ -> "never" },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue(final is HlsPublishState.Failure)
        assertEquals("server 500", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun masterUploadExceptionTransitionsToFailure() {
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = { _, _ -> throw RuntimeException("master upload failed") },
                signAndPublish = { _, _ -> "never" },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue(final is HlsPublishState.Failure)
        assertEquals("master upload failed", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun publishExceptionTransitionsToFailure() {
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { _, _ -> throw RuntimeException("relay rejected") },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val final = orchestrator.state.value
        assertTrue(final is HlsPublishState.Failure)
        assertEquals("relay rejected", (final as HlsPublishState.Failure).message)
    }

    @Test
    fun sensitiveContentPassesContentWarningIntoTemplate() {
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { tpl, _ ->
                    captured += tpl
                    "event-id"
                },
            )

        runBlocking {
            orchestrator.publish(newRequest(sensitive = true, warningReason = "NSFW"))
        }

        val template = (captured.single() as HlsVideoEventTemplate.Horizontal).template
        val cw = template.tags.firstOrNull { it.isNotEmpty() && it[0] == "content-warning" }
        assertNotNull(cw)
        assertEquals("NSFW", cw!![1])
    }

    @Test
    fun portraitRenditionsProduceVerticalTemplate() {
        val portrait =
            listOf(
                HlsRenditionSummary(
                    rendition = Rendition(Resolution.SD_360, 500),
                    mediaPlaylist = "",
                    playlistFilename = "360p/media.m3u8",
                    width = 360,
                    height = 640,
                    codecString = "avc1.64001f",
                    combinedFilename = "360p.mp4",
                ),
            )
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(portrait),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { tpl, _ ->
                    captured += tpl
                    "event-id"
                },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        assertTrue(captured.single() is HlsVideoEventTemplate.Vertical)
    }

    @Test
    fun posterUrlFromUploadPosterClosureLandsOnEveryImeta() {
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val capturedSiblings = mutableListOf<EventTemplate<TextNoteEvent>>()
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { tpl, sibling ->
                    captured += tpl
                    capturedSiblings += sibling
                    "event-id"
                },
                uploadPoster = { _ -> HlsPosterUpload("https://cdn.test/poster.jpg") },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        val template = (captured.single() as HlsVideoEventTemplate.Horizontal).template
        val imetas = template.tags.filter { it.isNotEmpty() && it[0] == "imeta" }
        assertTrue("expected at least one imeta", imetas.isNotEmpty())
        imetas.forEach { imeta ->
            val flat = imeta.joinToString("|")
            assertTrue("imeta missing poster: $flat", flat.contains("image https://cdn.test/poster.jpg"))
        }

        // Sibling kind:1 carries the same poster URL on its single imeta.
        val sibling = capturedSiblings.single()
        val siblingImeta = sibling.tags.firstOrNull { it.isNotEmpty() && it[0] == "imeta" }
        assertNotNull("sibling missing imeta", siblingImeta)
        val siblingFlat = siblingImeta!!.joinToString("|")
        assertTrue("sibling imeta missing poster: $siblingFlat", siblingFlat.contains("image https://cdn.test/poster.jpg"))
    }

    @Test
    fun posterFailureDoesNotAbortPublish() {
        val captured = mutableListOf<HlsVideoEventTemplate>()
        val canned = CannedUploader()
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = fakeRunUpload(),
                buildUploader = { canned },
                uploadMaster = fakeUploadMaster(canned),
                signAndPublish = { tpl, _ ->
                    captured += tpl
                    "event-id"
                },
                uploadPoster = { _ -> throw RuntimeException("decode failed") },
            )

        runBlocking { orchestrator.publish(newRequest()) }

        // Publish still succeeded despite poster failure
        assertTrue(orchestrator.state.value is HlsPublishState.Success)
        // And the imetas have no `image ` property
        val template = (captured.single() as HlsVideoEventTemplate.Horizontal).template
        val imetas = template.tags.filter { it.isNotEmpty() && it[0] == "imeta" }
        imetas.forEach { imeta ->
            val flat = imeta.joinToString("|")
            assertTrue("imeta unexpectedly carries image after poster failure: $flat", !flat.contains("image "))
        }
    }

    @Test
    fun resetRestoresIdleState() {
        val orchestrator =
            HlsPublishOrchestrator(
                _state = MutableStateFlow(HlsPublishState.Idle),
                runUpload = { _, _, _ -> throw RuntimeException("boom") },
                buildUploader = { CannedUploader() },
                uploadMaster = { _, _ -> MediaUploadResult(url = "never") },
                signAndPublish = { _, _ -> "never" },
            )

        runBlocking { orchestrator.publish(newRequest()) }
        assertTrue(orchestrator.state.value is HlsPublishState.Failure)

        orchestrator.reset()
        assertEquals(HlsPublishState.Idle, orchestrator.state.value)
    }
}
