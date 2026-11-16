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
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ThumbnailDiskCacheInstrumentedTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var cacheDir: File
    private lateinit var cache: ThumbnailDiskCache
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        cacheDir = File(appContext.cacheDir, "thumbnail-test-${UUID.randomUUID()}")
        cache = ThumbnailDiskCache(cacheDir)
        sourceFile = File(appContext.cacheDir, "source-${UUID.randomUUID()}.jpg")
        val bitmap = createBitmap(64, 64)
        sourceFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        sourceFile.delete()
    }

    @Test
    fun generatesThumbnailFromJpeg() {
        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.jpg"

        assertTrue("generateFromFile must return true for a valid JPEG", cache.generateFromFile(url, sourceFile))
        assertNotNull("load() must return a non-null Bitmap for a cached JPEG", cache.load(url))
    }

    @Test
    fun recreatesCacheDirClearedAtRuntime() {
        // The system (cache trim) or the user (Settings > Clear cache) can delete
        // the cache dir while the app runs; the next write must recreate it
        // instead of failing with ENOENT.
        val url = "https://example.com/profile-pic-${UUID.randomUUID()}.jpg"
        assertTrue(cacheDir.deleteRecursively())

        assertTrue(
            "generateFromFile must recreate the cache dir after it is cleared at runtime",
            cache.generateFromFile(url, sourceFile),
        )
        assertNotNull("load() must return the thumbnail written after dir recreation", cache.load(url))
    }
}
