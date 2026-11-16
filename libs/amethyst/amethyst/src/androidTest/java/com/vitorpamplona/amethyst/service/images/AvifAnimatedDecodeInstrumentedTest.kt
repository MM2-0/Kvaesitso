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

import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.asDrawable
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.ScaleDrawable
import coil3.toBitmap
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.appContext
import com.vitorpamplona.amethyst.AvifInstrumentedTestSupport.copyAssetToCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvifAnimatedDecodeInstrumentedTest {
    private val avifLoader: ImageLoader by lazy {
        ImageLoader
            .Builder(appContext)
            .components {
                add(AvifAnimatedDecoderFactory())
                add(AnimatedImageDecoder.Factory())
            }.build()
    }

    @Test
    fun stillAvifDecodesToBitmap() =
        runBlocking {
            assumeTrue("AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
            val avif = copyAssetToCache("avif/still-tiny-8x8.avif")

            val result =
                avifLoader.execute(
                    ImageRequest.Builder(appContext).data(avif.toUri()).build(),
                )

            assertTrue("Expected SuccessResult, got ${result::class.simpleName}", result is SuccessResult)
            val bitmap = (result as SuccessResult).image.toBitmap()
            assertEquals(8, bitmap.width)
            assertEquals(8, bitmap.height)
        }

    @Test
    fun animatedAvifDecodesToAnimatedImageDrawable() =
        runBlocking {
            assumeTrue("Animated AVIF requires API 31+", Build.VERSION.SDK_INT >= 31)
            val avif = copyAssetToCache("avif/animated-tiny-3frames.avif")

            val result =
                avifLoader.execute(
                    ImageRequest.Builder(appContext).data(avif.toUri()).build(),
                )

            assertTrue("Expected SuccessResult", result is SuccessResult)
            val drawable = (result as SuccessResult).image.asDrawable(appContext.resources)
            // AnimatedImageDecoder wraps AnimatedImageDrawable in a ScaleDrawable.
            // Both are Animatable; a BitmapDrawable (the failure case) is not.
            assertTrue(
                "Animated AVIF must be Animatable — Coil's AvifAnimatedDecoderFactory was not invoked if this fails (got ${drawable::class.simpleName})",
                drawable is Animatable,
            )
            // Unwrap ScaleDrawable to confirm the inner drawable is AnimatedImageDrawable.
            val inner = if (drawable is ScaleDrawable) drawable.child else drawable
            assertTrue(
                "Inner drawable must be AnimatedImageDrawable, got ${inner::class.simpleName}",
                inner is AnimatedImageDrawable,
            )
        }
}
