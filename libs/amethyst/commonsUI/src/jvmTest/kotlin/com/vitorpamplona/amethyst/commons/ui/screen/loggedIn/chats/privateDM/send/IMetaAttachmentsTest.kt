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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.quartz.nip92IMeta.imetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * A pasted link to an image gets the same imeta an upload does, computed from the downloaded bytes;
 * links that are not images, or whose bytes do not decode, get none.
 */
class IMetaAttachmentsTest {
    private val httpClients =
        object : IRoleBasedHttpClientBuilder {
            override fun proxyPortForVideo(url: String): Int? = null

            override fun okHttpClientForNip05(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForUploads(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForImage(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForVideo(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForMoney(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForPreview(url: String): OkHttpClient = error("unused")

            override fun okHttpClientForPushRegistration(url: String): OkHttpClient = error("unused")
        }

    private class FakeUploader(
        val headers: Map<String, FileHeader?>,
    ) : MediaUploader by UnavailableMediaUploader {
        val downloads = mutableListOf<String>()

        override suspend fun remoteFileHeader(
            url: String,
            httpClients: IRoleBasedHttpClientBuilder,
        ): FileHeader? {
            downloads += url
            return headers[url]
        }
    }

    private fun image(hash: String) = FileHeader("image/jpeg", hash, 1234, DimensionTag(800, 600), null)

    private fun prepare(
        attachments: IMetaAttachments,
        urls: List<String>,
        uploader: MediaUploader,
        mimeTypes: Map<String, String> = emptyMap(),
    ) = runBlocking {
        coroutineScope {
            attachments.prepareLinkedImages(urls, this, uploader, httpClients) { mimeTypes[it] }
        }
    }

    @Test
    fun linkedImageGetsTheImetaOfItsBytes() {
        val url = "https://example.com/cat.jpg"
        val attachments = IMetaAttachments()

        prepare(attachments, listOf(url), FakeUploader(mapOf(url to image("aa"))))

        val imeta = attachments.iMetaAttachments.single()
        assertEquals(url, imeta.url)
        assertEquals("aa", imeta.properties["x"]?.first())
        assertEquals("1234", imeta.properties["size"]?.first())
        assertEquals("image/jpeg", imeta.properties["m"]?.first())
        assertEquals("800x600", imeta.properties["dim"]?.first())
    }

    @Test
    fun linkWithoutExtensionIsAnImageWhenItsPreviewSaysSo() {
        val image = "https://blossom.example.com/abcdef"
        val page = "https://example.com/article"
        val uploader = FakeUploader(mapOf(image to image("bb")))
        val attachments = IMetaAttachments()

        prepare(attachments, listOf(image, page), uploader, mapOf(image to "image/png", page to "text/html"))

        assertEquals(listOf(image), uploader.downloads)
        assertEquals(listOf(image), attachments.iMetaAttachments.map { it.url })
    }

    @Test
    fun videosAndUndecodableBytesGetNoImeta() {
        val video = "https://example.com/clip.mp4"
        val broken = "https://example.com/broken.png"
        val uploader = FakeUploader(mapOf(broken to FileHeader("image/png", "cc", 10, null, null)))
        val attachments = IMetaAttachments()

        prepare(attachments, listOf(video, broken), uploader)

        assertEquals(listOf(broken), uploader.downloads)
        assertTrue(attachments.iMetaAttachments.isEmpty())
    }

    @Test
    fun eachLinkIsDownloadedOnce() {
        val url = "https://example.com/cat.jpg"
        val uploader = FakeUploader(mapOf(url to null))
        val attachments = IMetaAttachments()

        prepare(attachments, listOf(url), uploader)
        prepare(attachments, listOf(url), uploader)

        assertEquals(listOf(url), uploader.downloads)
    }

    @Test
    fun anUploadsImetaIsKept() {
        val url = "https://example.com/cat.jpg"
        val uploader = FakeUploader(mapOf(url to image("dd")))
        val attachments = IMetaAttachments()
        attachments.add(imetaTagBuilder(url) { alt("a cat") })

        prepare(attachments, listOf(url), uploader)

        assertTrue(uploader.downloads.isEmpty())
        assertEquals(
            "a cat",
            attachments.iMetaAttachments
                .single()
                .properties["alt"]
                ?.first(),
        )
    }

    @Test
    fun aCancelledDownloadIsTriedAgain() {
        val url = "https://example.com/cat.jpg"
        val attachments = IMetaAttachments()
        val started = CompletableDeferred<Unit>()
        val stuck =
            object : MediaUploader by UnavailableMediaUploader {
                override suspend fun remoteFileHeader(
                    url: String,
                    httpClients: IRoleBasedHttpClientBuilder,
                ): FileHeader? {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    return null
                }
            }

        runBlocking {
            val job = Job()
            val scope = CoroutineScope(coroutineContext + job)
            attachments.prepareLinkedImages(listOf(url), scope, stuck, httpClients) { null }
            started.await()
            job.cancel()
            job.join()
        }

        val uploader = FakeUploader(mapOf(url to image("ee")))
        prepare(attachments, listOf(url), uploader)

        assertEquals(listOf(url), uploader.downloads)
        assertEquals(
            "ee",
            attachments.iMetaAttachments
                .single()
                .properties["x"]
                ?.first(),
        )
    }

    @Test
    fun concurrentWritersKeepEveryImeta() {
        val attachments = IMetaAttachments()
        val threads =
            (0 until 8).map { t ->
                Thread {
                    repeat(250) { i -> attachments.add(imetaTagBuilder("https://example.com/$t-$i.jpg") { alt("x") }) }
                }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(2000, attachments.iMetaAttachments.size)
    }

    @Test
    fun aPostWaitsForAnImageStillBeingMeasured() {
        val url = "https://example.com/slow.jpg"
        val release = CompletableDeferred<Unit>()
        val slow =
            object : MediaUploader by UnavailableMediaUploader {
                override suspend fun remoteFileHeader(
                    url: String,
                    httpClients: IRoleBasedHttpClientBuilder,
                ): FileHeader? {
                    release.await()
                    return image("ff")
                }
            }
        val attachments = IMetaAttachments()

        runBlocking {
            attachments.prepareLinkedImages(listOf(url), this, slow, httpClients) { null }
            launch {
                delay(200)
                release.complete(Unit)
            }
            attachments.awaitLinkedImages(timeoutMs = 5_000)

            assertEquals(listOf(url), attachments.filterIsIn(setOf(url)).map { it.url })
        }
    }

    @Test
    fun aPostDoesNotWaitPastTheLimit() {
        val stuck =
            object : MediaUploader by UnavailableMediaUploader {
                override suspend fun remoteFileHeader(
                    url: String,
                    httpClients: IRoleBasedHttpClientBuilder,
                ): FileHeader? {
                    CompletableDeferred<Unit>().await()
                    return null
                }
            }
        val attachments = IMetaAttachments()

        runBlocking {
            val job = Job()
            attachments.prepareLinkedImages(listOf("https://example.com/stuck.jpg"), CoroutineScope(coroutineContext + job), stuck, httpClients) { null }
            val waited = measureTime { attachments.awaitLinkedImages(timeoutMs = 300) }
            job.cancel()

            assertTrue(waited.inWholeMilliseconds in 250..2_000, "waited $waited")
            assertTrue(attachments.iMetaAttachments.isEmpty())
        }
    }
}
