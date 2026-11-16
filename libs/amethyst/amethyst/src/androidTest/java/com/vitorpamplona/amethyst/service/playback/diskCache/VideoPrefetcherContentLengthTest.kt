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
package com.vitorpamplona.amethyst.service.playback.diskCache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/**
 * An mp4 may end in an `mdat` box of size 0, meaning "to the end of the file". media3's sniffer can
 * only size that box when it knows how long the file is, and without a length no extractor accepts
 * the stream: the viewer gets ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED for a video every browser
 * plays. So a prefetched head has to leave the file's length in the cache, or the player that opens
 * it reads a stream of unknown length.
 *
 * Runs on a real [androidx.media3.datasource.cache.SimpleCache] and a real [OkHttpDataSource] over a
 * canned OkHttp response, because the bugs here live in how those two hand the length over.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoPrefetcherContentLengthTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val video = openEndedMp4(1_000_000)
    private val cacheDir = File(context.cacheDir, "prefetcher-test-${UUID.randomUUID()}")
    private val videoCache = VideoCache().apply { initFileCache(context, cacheDir) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
        videoCache.simpleCache.release()
        cacheDir.deleteRecursively()
    }

    @Test
    fun aPrefetchedHeadKeepsAnOpenEndedMp4Playable() = assertPlayableAfterPrefetch(hostServing(honoursRange = true))

    @Test
    fun theLengthIsKnownWhenTheServerIgnoresTheRangeRequest() = assertPlayableAfterPrefetch(hostServing(honoursRange = false))

    // A wrong length is worse than none: stored, it would end the video at the slice for as long as
    // the head stays cached.
    @Test
    fun aSliceWithNoContentRangeIsNotTakenForTheWholeFile() {
        val server = hostServing(honoursRange = true, sendsContentRange = false)

        prefetchHead(server)

        openLikeThePlayer(server) { _, length -> assertEquals(C.LENGTH_UNSET.toLong(), length) }
    }

    private fun assertPlayableAfterPrefetch(server: DataSource.Factory) {
        prefetchHead(server)

        openLikeThePlayer(server) { source, length ->
            assertEquals(video.size.toLong(), length)
            assertTrue(
                "the mp4 extractor must accept the stream",
                Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED).sniff(DefaultExtractorInput(source, 0, length)),
            )
        }
    }

    private fun prefetchHead(server: DataSource.Factory) {
        VideoPrefetcher({ videoCache }, { server }, scope).prefetch(this, listOf(VideoPrefetcher.Video(URL, useProxy = false)))

        runBlocking {
            withTimeout(10_000) {
                while (videoCache.simpleCache.getCachedBytes(URL, 0, VideoPrefetcher.PREFETCH_BYTES) < VideoPrefetcher.PREFETCH_BYTES) {
                    delay(20)
                }
            }
        }
    }

    // The player's cache route and an open with no length, which reports what the cache knows.
    private fun openLikeThePlayer(
        server: DataSource.Factory,
        check: (DataSource, Long) -> Unit,
    ) {
        val source = videoCache.get(server).createDataSource()
        try {
            check(source, source.open(DataSpec.Builder().setUri(URL).build()))
        } finally {
            source.close()
        }
    }

    private fun hostServing(
        honoursRange: Boolean,
        sendsContentRange: Boolean = true,
    ): DataSource.Factory =
        OkHttpDataSource.Factory(
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val range = if (honoursRange) RANGE.find(chain.request().header("Range").orEmpty()) else null
                    val start = range?.groupValues?.get(1)?.toInt() ?: 0
                    val end =
                        range
                            ?.groupValues
                            ?.get(2)
                            ?.toIntOrNull()
                            ?.coerceAtMost(video.lastIndex) ?: video.lastIndex
                    val bytes = video.copyOfRange(start, end + 1)
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_2)
                        .code(if (range != null) 206 else 200)
                        .message("")
                        .header("Content-Length", bytes.size.toString())
                        .apply { if (range != null && sendsContentRange) header("Content-Range", "bytes $start-$end/${video.size}") }
                        .body(bytes.toResponseBody("video/mp4".toMediaType()))
                        .build()
                }.build(),
        )

    private companion object {
        const val URL = "https://media.example/4529b13894e51742e137dff7a4db703890cd0a487fa2bd2c25ecae1d99c61be4.mp4"
        val RANGE = Regex("bytes=(\\d+)-(\\d*)")

        // ftyp, an empty moov, and an `mdat` of size 0 running to the end of [size] bytes.
        fun openEndedMp4(size: Int): ByteArray =
            ByteBuffer
                .allocate(size)
                .putInt(24)
                .put("ftypmp42".toByteArray())
                .putInt(0)
                .put("isommp42".toByteArray())
                .putInt(8)
                .put("moov".toByteArray())
                .putInt(0)
                .put("mdat".toByteArray())
                .array()
    }
}
