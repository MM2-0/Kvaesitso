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
package com.vitorpamplona.amethyst.service.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Gainmap
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.commons.service.image.OkHttpFactory
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MetadataStripper
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Walks an Ultra HDR JPEG (a JPEG with an ISO 21496-1 / Android gain map) through every stage an
 * uploaded or viewed photo passes, asserting the gain map survives each one. Losing it anywhere
 * turns the photo into plain SDR, which is what "HDR photos don't work" looks like to a user.
 */
@RunWith(AndroidJUnit4::class)
class HdrGainmapPipelineInstrumentedTest {
    private lateinit var hdrJpeg: File

    @Before
    fun setUp() {
        assumeTrue("Gain maps need API 34+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        hdrJpeg = File(appContext.cacheDir.also { it.mkdirs() }, "${UUID.randomUUID()}.jpg")
        val fixture = ultraHdrBitmap()
        try {
            hdrJpeg.outputStream().use { fixture.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            fixture.recycleWithGainmap()
        }
    }

    @After
    fun tearDown() {
        if (::hdrJpeg.isInitialized) hdrJpeg.delete()
    }

    @Test
    fun theFixtureIsAnUltraHdrJpeg() {
        assertTrue("fixture lost its gain map", decodesWithGainmap(hdrJpeg))
    }

    @Test
    fun metadataStrippingKeepsTheGainmap() {
        val stripped = MetadataStripper.stripImageMetadata(hdrJpeg.toUri(), appContext)
        assertTrue("stripper did not run", stripped.stripped)
        assertTrue("stripping dropped the gain map", decodesWithGainmap(File(requireNotNull(stripped.uri.path))))
    }

    @Test
    fun compressionKeepsTheGainmap() =
        runBlocking<Unit> {
            val out = compress(hdrJpeg)
            val bounds = bounds(out)
            // 4000x3000 against the compressor's 640x816 target: BitmapFactory subsamples by 2.
            assertEquals("the compressor did not downscale", 2000, bounds.outWidth)
            assertTrue("compression dropped the gain map", decodesWithGainmap(out))
        }

    @Test
    fun compressionOfARotatedPhotoKeepsTheGainmap() =
        runBlocking<Unit> {
            // Portrait phone photos are stored landscape plus an EXIF rotation, which the
            // compressor bakes in through Bitmap.createBitmap(..., matrix, ...).
            ExifInterface(hdrJpeg).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val out = compress(hdrJpeg)
            val bounds = bounds(out)
            assertTrue("the compressor did not rotate", bounds.outHeight > bounds.outWidth)
            assertTrue("rotating dropped the gain map", decodesWithGainmap(out))
        }

    @Test
    fun coilDecodeKeepsTheGainmap() =
        runBlocking<Unit> {
            // Coil owns the bitmaps it returns (its memory cache may hand them out again), so
            // release them by shutting the loader down rather than recycling them here.
            val loader = ImageLoader.Builder(appContext).build()
            try {
                for (hardware in listOf(true, false)) {
                    val request =
                        ImageRequest
                            .Builder(appContext)
                            .data(hdrJpeg)
                            .size(400, 300)
                            .allowHardware(hardware)
                            .build()
                    val result = loader.execute(request) as SuccessResult
                    val bitmap = (result.image as BitmapImage).bitmap
                    assertTrue("Coil dropped the gain map (allowHardware=$hardware)", bitmap.hasGainmap())
                }
            } finally {
                loader.shutdown()
            }
        }

    /**
     * A feed image as the app loads it: over HTTP through [OkHttpFactory] (which re-homes the
     * disk-cache file so the platform ImageDecoder is reachable), then again from the memory
     * cache, then from the disk cache after the memory cache is gone.
     */
    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun coilCachesKeepTheGainmap() =
        runBlocking<Unit> {
            val cacheDir = File(appContext.cacheDir, "hdr-coil-${UUID.randomUUID()}")
            val bytes = hdrJpeg.readBytes()
            ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
                serveForever(server, bytes)
                val memoryCache = MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build()
                val loader =
                    ImageLoader
                        .Builder(appContext)
                        .memoryCache(memoryCache)
                        .diskCache(DiskCache.Builder().directory(cacheDir.toOkioPath()).build())
                        .precision(Precision.INEXACT)
                        .components {
                            val client = OkHttpClient()
                            add(OkHttpFactory({ client }, DeDupeConcurrentRequestStrategy()) { fetcher, key -> fetcher.onSystemFileSystem(key) })
                        }.build()
                val url = "http://127.0.0.1:${server.localPort}/hdr.jpg"
                val request =
                    ImageRequest
                        .Builder(appContext)
                        .data(url)
                        .size(400, 300)
                        .build()

                suspend fun load(expected: DataSource) {
                    val result = loader.execute(request)
                    if (result is ErrorResult) throw AssertionError("$expected load failed", result.throwable)
                    result as SuccessResult
                    assertEquals(expected, result.dataSource)
                    assertTrue("gain map lost on a $expected load", (result.image as BitmapImage).bitmap.hasGainmap())
                }

                try {
                    load(DataSource.NETWORK)
                    load(DataSource.MEMORY_CACHE)
                    memoryCache.clear()
                    load(DataSource.DISK)
                } finally {
                    loader.shutdown()
                }
            }
            cacheDir.deleteRecursively()
        }

    private suspend fun compress(file: File): File {
        val compressed = MediaCompressor().compress(file.toUri(), "image/jpeg", CompressorQuality.MEDIUM, appContext)
        assertEquals("the compressor fell back to the original", "image/jpeg", compressed.contentType)
        val out = File(requireNotNull(compressed.uri.path))
        assertTrue("the compressor returned the original file", out.absolutePath != file.absolutePath)
        return out
    }

    private fun bounds(file: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.absolutePath, it) }

    // Full-size decodes of a 4000x3000 photo: recycle them rather than wait on the GC between tests.
    private fun decodesWithGainmap(file: File): Boolean {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        return try {
            bitmap.hasGainmap()
        } finally {
            bitmap.recycleWithGainmap()
        }
    }

    // The gain map is a separate bitmap; recycling the base does not free it.
    private fun Bitmap.recycleWithGainmap() {
        gainmap?.gainmapContents?.recycle()
        recycle()
    }

    private fun serveForever(
        server: ServerSocket,
        body: ByteArray,
    ) = thread(isDaemon = true) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            socket.use {
                val input = it.getInputStream().bufferedReader()
                while (input.readLine()?.isNotEmpty() == true) continue
                it.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body)
                    flush()
                }
            }
        }
    }

    private fun ultraHdrBitmap(): Bitmap {
        val base = createBitmap(4000, 3000).apply { eraseColor(Color.rgb(120, 140, 160)) }
        val contents = createBitmap(1000, 750).apply { eraseColor(Color.rgb(200, 200, 200)) }
        base.gainmap =
            Gainmap(contents).apply {
                setRatioMax(4f, 4f, 4f)
                setDisplayRatioForFullHdr(4f)
            }
        return base
    }
}
