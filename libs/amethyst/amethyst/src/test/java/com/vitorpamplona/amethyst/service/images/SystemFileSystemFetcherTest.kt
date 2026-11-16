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

import coil3.ColorImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import com.vitorpamplona.amethyst.commons.service.image.DeferredDeleteFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Coil hands the platform `ImageDecoder` a file only when the source's file system is
 * *referentially* `FileSystem.SYSTEM` (`ImageSource.toImageDecoderSourceOrNull`). Our disk cache
 * wraps it in a [DeferredDeleteFileSystem], so without this fetcher every network image loses
 * that path — silently: the decode still succeeds, it just copies the encoded image into RAM
 * first (animated) or drops to `BitmapFactoryDecoder` (static). These tests pin the property that
 * identity check reads, and the ownership transfer that keeps the disk-cache snapshot alive
 * exactly as long as the source that replaces it.
 */
class SystemFileSystemFetcherTest {
    private lateinit var tmpRoot: File
    private lateinit var file: Path
    private lateinit var scope: CoroutineScope
    private lateinit var deferredDelete: DeferredDeleteFileSystem

    private val bytes = ByteArray(64) { it.toByte() }

    @Before
    fun setUp() {
        tmpRoot = Files.createTempDirectory("system-file-system-fetcher-test").toFile()
        file = tmpRoot.resolve("blob.gif").toOkioPath()
        FileSystem.SYSTEM.write(file) { write(bytes) }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        deferredDelete = DeferredDeleteFileSystem(FileSystem.SYSTEM, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tmpRoot.deleteRecursively()
    }

    private class FakeFetcher(
        private val result: FetchResult?,
    ) : Fetcher {
        override suspend fun fetch() = result
    }

    /** Records whether the disk-cache snapshot Coil would hand us has been released. */
    private class Snapshot : AutoCloseable {
        var closed = false
            private set

        override fun close() {
            closed = true
        }
    }

    private fun diskCacheSource(snapshot: AutoCloseable? = null) =
        ImageSource(
            file = file,
            fileSystem = deferredDelete,
            diskCacheKey = "https://example.com/blob.gif",
            closeable = snapshot,
        )

    @Test
    fun aDiskCacheSourceIsHandedOnTheSystemFileSystem() =
        runTest {
            val fetcher =
                FakeFetcher(SourceFetchResult(diskCacheSource(), "image/gif", DataSource.DISK))
                    .onSystemFileSystem("https://example.com/blob.gif")

            val result = fetcher.fetch() as SourceFetchResult

            assertSame(
                "Coil only reaches ImageDecoder when this is referentially FileSystem.SYSTEM",
                FileSystem.SYSTEM,
                result.source.fileSystem,
            )
            assertSame(file, result.source.fileOrNull())
            assertArrayEquals(bytes, result.source.source().readByteArray())
            result.source.close()
        }

    @Test
    fun theRestOfTheResultSurvivesTheSwap() =
        runTest {
            val fetcher =
                FakeFetcher(SourceFetchResult(diskCacheSource(), "image/gif", DataSource.NETWORK))
                    .onSystemFileSystem("https://example.com/blob.gif")

            val result = fetcher.fetch() as SourceFetchResult

            assertEquals("image/gif", result.mimeType)
            assertEquals(DataSource.NETWORK, result.dataSource)
        }

    @Test
    fun closingTheReplacementReleasesTheDiskCacheSnapshot() =
        runTest {
            // The engine closes exactly one source per fetch, and after the swap that is the
            // replacement. If it did not own the original, the snapshot would leak and the cache
            // entry would stay open forever.
            val snapshot = Snapshot()
            val fetcher =
                FakeFetcher(SourceFetchResult(diskCacheSource(snapshot), null, DataSource.DISK))
                    .onSystemFileSystem("https://example.com/blob.gif")

            val result = fetcher.fetch() as SourceFetchResult
            assertFalse(snapshot.closed)

            result.source.close()

            assertTrue("closing the re-homed source must release the snapshot", snapshot.closed)
        }

    @Test
    fun theDiskCacheKeyIsCarriedOver() =
        runTest {
            // FileImageSource.diskCacheKey is internal to Coil, but the engine reads it off the
            // source to populate SuccessResult.diskCacheKey. Rebuilding the source without it
            // would drop that silently, so reach for the field directly.
            val fetcher =
                FakeFetcher(SourceFetchResult(diskCacheSource(), null, DataSource.DISK))
                    .onSystemFileSystem("https://example.com/blob.gif")

            val result = fetcher.fetch() as SourceFetchResult

            val field =
                result.source.javaClass.declaredFields
                    .firstOrNull { it.name == "diskCacheKey" }
            assertNotNull("Coil renamed FileImageSource.diskCacheKey; re-check the re-home", field)
            field!!.isAccessible = true
            assertEquals("https://example.com/blob.gif", field.get(result.source))
        }

    @Test
    fun aSourceAlreadyOnTheSystemFileSystemIsLeftAlone() =
        runTest {
            val original = SourceFetchResult(ImageSource(file, FileSystem.SYSTEM), null, DataSource.DISK)
            val fetcher = FakeFetcher(original).onSystemFileSystem("key")

            assertSame(original, fetcher.fetch())
        }

    @Test
    fun anUnknownFileSystemWrapperIsLeftAlone() =
        runTest {
            // Only DeferredDeleteFileSystem is known to forward reads verbatim. Anything else may
            // rewrite paths or serve different bytes, so the file underneath is not ours to hand out.
            val original =
                SourceFetchResult(
                    ImageSource(file, object : ForwardingFileSystem(FileSystem.SYSTEM) {}),
                    null,
                    DataSource.DISK,
                )
            val fetcher = FakeFetcher(original).onSystemFileSystem("key")

            assertSame(original, fetcher.fetch())
        }

    @Test
    fun aStreamBackedSourceIsNotMaterialisedIntoATempFile() =
        runTest {
            val source = ImageSource(Buffer().write(bytes), deferredDelete)
            val original = SourceFetchResult(source, null, DataSource.NETWORK)
            val fetcher = FakeFetcher(original).onSystemFileSystem("key")

            assertSame(original, fetcher.fetch())
            assertNull("re-homing must not force a temp copy of a stream", source.fileOrNull())
        }

    @Test
    fun aNonSourceResultPassesStraightThrough() =
        runTest {
            // ProfilePictureFetcher answers thumbnail hits with an ImageFetchResult.
            val original = ImageFetchResult(ColorImage(0), isSampled = true, DataSource.DISK)

            assertSame(original, FakeFetcher(original).onSystemFileSystem("key").fetch())
        }

    @Test
    fun aFetcherThatDeclinesStillDeclines() =
        runTest {
            assertNull(FakeFetcher(null).onSystemFileSystem("key").fetch())
        }
}
