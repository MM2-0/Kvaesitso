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
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.annotation.InternalCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.decode.StaticImageDecoder
import coil3.decode.toImageDecoderSourceOrNull
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Pins the platform-side half of [SystemFileSystemFetcher], which the JVM unit tests cannot
 * reach: `android.graphics.ImageDecoder` is what Coil's identity check gates access to, so only
 * a device can show that the check really does fail on our disk cache's file system and really
 * does pass once the source is re-homed.
 *
 * If these ever start passing without the re-home, Coil has loosened the check and
 * [SystemFileSystemFetcher] can go.
 */
@RunWith(AndroidJUnit4::class)
class SystemFileSystemImageDecoderInstrumentedTest {
    private lateinit var pngFile: File
    private lateinit var path: Path
    private lateinit var scope: CoroutineScope
    private lateinit var deferredDelete: DeferredDeleteFileSystem

    private val imageLoader by lazy { ImageLoader.Builder(appContext).build() }
    private val options by lazy { Options(appContext) }

    @Before
    fun setUp() {
        pngFile = File(appContext.cacheDir.also { it.mkdirs() }, "${UUID.randomUUID()}.png")
        pngFile.outputStream().use { createBitmap(4, 4).compress(Bitmap.CompressFormat.PNG, 100, it) }
        path = pngFile.toOkioPath()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        deferredDelete = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        pngFile.delete()
    }

    /** How Coil's `NetworkFetcher` builds the source when the disk cache wraps its file system. */
    private fun diskCacheSource() = ImageSource(file = path, fileSystem = deferredDelete, diskCacheKey = KEY)

    private fun reHomedSource() = requireNotNull(diskCacheSource().onSystemFileSystem(KEY))

    @OptIn(InternalCoilApi::class)
    @Test
    fun ourDiskCacheFileSystemHidesTheFileFromImageDecoder() {
        assertNull(
            "still decode path",
            diskCacheSource().toImageDecoderSourceOrNull(options, animated = false),
        )
        assertNull(
            "animated decode path",
            diskCacheSource().toImageDecoderSourceOrNull(options, animated = true),
        )
    }

    @OptIn(InternalCoilApi::class)
    @Test
    fun theReHomedSourceReachesImageDecoder() {
        assertNotNull(
            "still decode path",
            reHomedSource().toImageDecoderSourceOrNull(options, animated = false),
        )
        assertNotNull(
            "animated decode path",
            reHomedSource().toImageDecoderSourceOrNull(options, animated = true),
        )
    }

    @Test
    fun staticImageDecoderDeclinesOurDiskCacheSourceAndAcceptsTheReHomedOne() {
        assumeTrue("StaticImageDecoder requires API 29+", Build.VERSION.SDK_INT >= 29)

        // Declining is why every still image in the app was decoding through BitmapFactoryDecoder.
        assertNull(
            StaticImageDecoder.Factory().create(fetchResult(diskCacheSource()), options, imageLoader),
        )
        assertNotNull(
            StaticImageDecoder.Factory().create(fetchResult(reHomedSource()), options, imageLoader),
        )
    }

    private fun fetchResult(source: ImageSource) = SourceFetchResult(source, "image/png", DataSource.DISK)

    companion object {
        private const val KEY = "https://example.com/blob.png"
    }
}
