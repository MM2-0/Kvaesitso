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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.contentUriFor
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.copyAssetToCache
import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_MIME
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvifMetadataStripperPoisonedFixtureInstrumentedTest {
    @Test
    fun stripDispatcherThrowsAvifMetadataNotVerifiableExceptionForPoisonedAvif() {
        // Regression for commit b9112550b: NewUserMetadataViewModel calls
        // MetadataStripper.strip(uri, "image/avif", context) at line 218.
        // The viewmodel only catches AvifMetadataNotVerifiableException to
        // surface the AVIF-specific error string; any other exception type
        // would fall through to "Upload cancelled" and confuse the user.
        //
        // Note: strip() dispatches by mimeType param, then internally
        // stripImageMetadata() re-resolves via contentResolver.getType(uri).
        // A content:// URI (FileProvider) is required so getType() returns
        // "image/avif" and the AVIF inspection branch is reached.
        val avif = copyAssetToCache("avif/still-tiny-8x8-exif-gps.avif")
        val uri = contentUriFor(avif)
        val ex =
            assertThrows(AvifMetadataNotVerifiableException::class.java) {
                runBlocking { MetadataStripper.strip(uri, AVIF_MIME, appContext) }
            }
        assertNotNull("Exception must have a non-null message for UI display", ex.message)
    }
}
