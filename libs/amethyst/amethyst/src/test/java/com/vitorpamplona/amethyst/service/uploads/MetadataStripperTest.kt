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
package com.vitorpamplona.amethyst.service.uploads

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.davotoula.lightcompressor.sanitizer.Mp4Sanitizer
import com.davotoula.lightcompressor.sanitizer.SanitizeResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

class MetadataStripperTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        uri = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
        every { context.cacheDir } returns tempFolder.root

        // File.toUri() is Uri.fromFile, a stub on the JVM: hand back a Uri that remembers its path.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers { fileUri(firstArg()) }

        // Mp4Sanitizer's API is @JvmStatic: Kotlin calls the static bridge, which mockkObject
        // does not intercept, so mock the static methods instead.
        mockkStatic(Mp4Sanitizer::class)
    }

    private fun fileUri(file: File): Uri = mockk { every { path } returns file.absolutePath }

    /** Stubs [Mp4Sanitizer.sanitize] to succeed and report [fastStart]; returns the slot holding its output file. */
    private fun sanitizeSucceeds(fastStart: Boolean = true) =
        slot<File>().also { output ->
            every { Mp4Sanitizer.sanitize(context, uri, capture(output)) } answers {
                SanitizeResult(output.captured, fastStart)
            }
        }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `AVIF with no sensitive tags returns original uri marked stripped`() {
        // Arrange: AVIF content; tag reader returns null for every tag (no sensitive tags).
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        // Act: inject a pure-lambda tag reader — no ExifInterface instantiation needed.
        val result =
            MetadataStripper.stripImageMetadata(uri, context) { _ ->
                { _: String -> null }
            }

        // Assert: original URI returned, marked stripped (i.e. "verified clean").
        assertEquals(uri, result.uri)
        assertTrue(result.stripped)
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF with GPS EXIF throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; tag reader reports GPS_LATITUDE present.
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        // Act: should throw before returning a StrippingResult
        MetadataStripper.stripImageMetadata(uri, context) { _ ->
            { tag: String ->
                if (tag == ExifInterface.TAG_GPS_LATITUDE) "40.7128" else null
            }
        }

        // Assert: handled by `expected` annotation; reaching here is a failure
        fail("Expected AvifMetadataNotVerifiableException, but no exception was thrown")
    }

    @Test(expected = AvifMetadataNotVerifiableException::class)
    fun `AVIF where ExifInterface fails throws AvifMetadataNotVerifiableException`() {
        // Arrange: AVIF content; tag reader factory throws on invocation (simulates parse failure).
        every { resolver.getType(uri) } returns "image/avif"
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

        MetadataStripper.stripImageMetadata(uri, context) { _ ->
            throw RuntimeException("malformed AVIF")
        }
        fail("Expected AvifMetadataNotVerifiableException")
    }

    @Test
    fun `video is sanitized into a cache file and marked stripped`() =
        runTest {
            val output = sanitizeSucceeds()

            val result = MetadataStripper.strip(uri, "video/mp4", context)

            verify(exactly = 1) { Mp4Sanitizer.sanitize(context, uri, any()) }
            assertEquals(tempFolder.root, output.captured.parentFile)
            assertTrue(output.captured.name.startsWith("stripped_video_"))
            assertEquals(output.captured.absolutePath, result.uri.path)
            assertTrue(result.stripped)
        }

    @Test
    fun `MP4 audio and AAC are sanitized`() =
        runTest {
            val output = sanitizeSucceeds()

            for (mime in listOf("audio/mp4", "audio/aac")) {
                val result = MetadataStripper.strip(uri, mime, context)

                assertTrue(output.captured.name.startsWith("stripped_audio_"))
                assertEquals(output.captured.absolutePath, result.uri.path)
                assertTrue(result.stripped)
            }
            verify(exactly = 2) { Mp4Sanitizer.sanitize(context, uri, any()) }
        }

    @Test
    fun `MP3 takes the ID3 path, not the sanitizer`() =
        runTest {
            every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(0))

            val result = MetadataStripper.strip(uri, "audio/mpeg", context)

            verify(exactly = 0) { Mp4Sanitizer.sanitize(any(), any(), any()) }
            // An empty MP3 has no ID3 tags: already clean, passed through as is.
            assertSame(uri, result.uri)
            assertTrue(result.stripped)
        }

    @Test
    fun `a track MP4 cannot hold returns the original unstripped and leaves no temp file`() =
        runTest {
            every { Mp4Sanitizer.sanitize(context, uri, any()) } throws IOException("track 1 (audio/opus) cannot be written to MP4")

            val result = MetadataStripper.strip(uri, "video/mp4", context)

            assertSame(uri, result.uri)
            assertFalse(result.stripped)
            assertEquals(emptyList<String>(), tempFolder.root.list()!!.toList())
        }

    @Test
    fun `an unexpected sanitizer exception returns the original unstripped and leaves no temp file`() =
        runTest {
            every { Mp4Sanitizer.sanitize(context, uri, any()) } throws IllegalStateException("muxer state")

            val result = MetadataStripper.strip(uri, "video/mp4", context)

            assertSame(uri, result.uri)
            assertFalse(result.stripped)
            assertEquals(emptyList<String>(), tempFolder.root.list()!!.toList())
        }

    @Test
    fun `a remux that is not fast-start is still stripped`() =
        runTest {
            val output = sanitizeSucceeds(fastStart = false)

            val result = MetadataStripper.strip(uri, "video/mp4", context)

            assertTrue(result.stripped)
            assertEquals(output.captured.absolutePath, result.uri.path)
            assertTrue(output.captured.exists())
        }

    @Test
    fun `an upload cancelled during the remux ends cancelled, not as a failed strip`() =
        runTest {
            lateinit var upload: Job
            every { Mp4Sanitizer.sanitize(context, uri, any()) } answers {
                upload.cancel()
                throw InterruptedIOException("sample copy interrupted")
            }
            var result: StrippingResult? = null

            upload = launch { result = MetadataStripper.strip(uri, "video/mp4", context) }
            upload.join()

            assertTrue(upload.isCancelled)
            assertNull(result)
            assertEquals(emptyList<String>(), tempFolder.root.list()!!.toList())
        }

    @Test
    fun `an upload cancelled just as the remux finishes deletes the stripped file`() =
        runTest {
            lateinit var upload: Job
            every { Mp4Sanitizer.sanitize(context, uri, any()) } answers {
                upload.cancel()
                SanitizeResult(thirdArg(), fastStart = true)
            }
            var result: StrippingResult? = null

            upload = launch { result = MetadataStripper.strip(uri, "video/mp4", context) }
            upload.join()

            assertTrue(upload.isCancelled)
            assertNull(result)
            assertEquals(emptyList<String>(), tempFolder.root.list()!!.toList())
        }
}
