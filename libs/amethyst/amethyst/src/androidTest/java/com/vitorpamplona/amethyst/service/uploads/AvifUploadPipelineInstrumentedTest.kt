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

import android.os.Build
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.contentUriFor
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.copyAssetToCache
import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_MIME
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AvifUploadPipelineInstrumentedTest {
    /**
     * Drives [assetPath] through [MediaCompressor.compress] with [AVIF_MIME] and asserts the
     * bytes survive unchanged. AVIF must bypass JPEG re-encoding regardless of whether the
     * source is still or animated.
     */
    private fun assertAvifBytesPreserved(
        assetPath: String,
        description: String,
    ) = runBlocking {
        val avif = copyAssetToCache(assetPath)
        val originalBytes = avif.readBytes()

        val result =
            MediaCompressor().compress(
                uri = avif.toUri(),
                contentType = AVIF_MIME,
                applicationContext = appContext,
                mediaQuality = CompressorQuality.MEDIUM,
            )

        assertEquals(AVIF_MIME, result.contentType)
        val resultBytes = File(result.uri.path!!).readBytes()
        assertTrue(description, originalBytes.contentEquals(resultBytes))
    }

    @Test
    fun stillAvifPassesThroughMediaCompressorUnchanged() =
        assertAvifBytesPreserved(
            "avif/still-tiny-8x8.avif",
            "Still AVIF bytes must be preserved through MediaCompressor",
        )

    @Test
    fun animatedAvifPassesThroughMediaCompressorUnchanged() =
        assertAvifBytesPreserved(
            "avif/animated-tiny-3frames.avif",
            "Animated AVIF bytes must be preserved (headline regression to prevent)",
        )

    @Test
    fun avifMetadataStripperReturnsCleanFile() {
        // Use a content:// URI via FileProvider so contentResolver.getType() returns AVIF_MIME.
        // ContentResolver.getType() returns null for file:// URIs even on API 36 (AVIF is not
        // recognised via MimeTypeMap for the file:// scheme). FileProvider uses
        // MimeTypeMap.getSingleton().getMimeTypeFromExtension("avif") which returns "image/avif"
        // on API 31+, matching production behaviour (gallery pickers always deliver content://).
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")
        val uri = contentUriFor(avif)
        val result = MetadataStripper.stripImageMetadata(uri, appContext)
        assertTrue("Clean AVIF should be marked stripped=true", result.stripped)
        assertEquals("Clean AVIF URI should be the original", uri, result.uri)
    }

    @Test
    fun avifPreviewMetadataGeneratesBlurhashAndThumbhash() {
        assumeTrue("AVIF decoding requires API 31+", Build.VERSION.SDK_INT >= 31)
        val avif = copyAssetToCache("avif/still-tiny-8x8.avif")

        val result =
            PreviewMetadataCalculator.computeFromUri(
                context = appContext,
                uri = avif.toUri(),
                mimeType = AVIF_MIME,
            )

        assertNotNull("PreviewMetadataCalculator must return non-null on API 31+", result)
        assertNotNull("AVIF blurhash should be generated via ImageDecoder", result!!.blurhash)
        assertNotNull("AVIF thumbhash should be generated via ImageDecoder", result.thumbhash)
        assertNotNull("AVIF dimensions should be returned", result.dim)
        val dim = result.dim!!
        assertEquals(8, dim.width)
        assertEquals(8, dim.height)
    }

    @Test
    fun poisonedAvifMetadataStripperThrowsAvifMetadataNotVerifiableException() {
        val avif = copyAssetToCache("avif/still-tiny-8x8-exif-gps.avif")
        val ex =
            assertThrows(AvifMetadataNotVerifiableException::class.java) {
                MetadataStripper.stripImageMetadata(contentUriFor(avif), appContext)
            }
        assertNotNull("Exception must have a non-null message", ex.message)
    }
}
