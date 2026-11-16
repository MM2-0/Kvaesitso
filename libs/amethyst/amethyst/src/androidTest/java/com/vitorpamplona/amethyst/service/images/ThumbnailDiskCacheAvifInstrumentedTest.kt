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

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.copyAssetToCache
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ThumbnailDiskCacheAvifInstrumentedTest {
    private lateinit var cacheDir: File
    private lateinit var cache: ThumbnailDiskCache

    @Before
    fun setUp() {
        cacheDir = File(appContext.cacheDir, "thumbnail-test-${UUID.randomUUID()}")
        cache = ThumbnailDiskCache(cacheDir)
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun animatedAvifIsNotCached() {
        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.avif"
        val source = copyAssetToCache("avif/animated-tiny-3frames.avif")

        val saved = cache.generateFromFile(url, source)

        assertFalse(
            "generateFromFile must return false for animated AVIF (would otherwise freeze avatar on first frame)",
            saved,
        )
        assertNull(
            "load() must return null for an animated-AVIF URL that was skipped",
            cache.load(url),
        )
    }

    @Test
    fun stillAvifIsCachedAsBitmap() {
        // generateFromFile uses BitmapFactory.decodeFile to read the source,
        // which requires platform AVIF decode support (API 31+). On older
        // devices the still-AVIF path returns false because decode fails,
        // not because of the animated-skip branch.
        assumeTrue("Still AVIF decode requires API 31+", Build.VERSION.SDK_INT >= 31)

        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.avif"
        val source = copyAssetToCache("avif/still-tiny-8x8.avif")

        val saved = cache.generateFromFile(url, source)

        assertTrue("generateFromFile must return true for still AVIF", saved)
        val bitmap = cache.load(url)
        assertNotNull("load() must return a non-null Bitmap for a cached still AVIF", bitmap)
    }
}
