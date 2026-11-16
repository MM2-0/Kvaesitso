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
package com.vitorpamplona.amethyst.commons.blurhash

import androidx.collection.LruCache
import kotlin.math.PI
import kotlin.math.cos

object CosineCache {
    // cache Math.cos() calculations to improve performance.
    // The number of calculations can be huge for many bitmaps: width * height * numCompX * numCompY *
    // 2 * nBitmaps
    // the cache is enabled by default, it is recommended to disable it only when just a few images
    // are displayed
    // Keyed on (size, components) as a pair, not their product: (50, 4) and
    // (100, 2) both produce a 200-entry table with different contents.
    private val cacheCosinesX = LruCache<Long, DoubleArray>(20)
    private val cacheCosinesY = LruCache<Long, DoubleArray>(20)

    private fun key(
        size: Int,
        components: Int,
    ): Long = (size.toLong() shl 32) or components.toLong()

    /**
     * Clear calculations stored in memory cache. The cache is not big, but will increase when many
     * image sizes are used, if the app needs memory it is recommended to clear it.
     */
    fun clearCache() {
        cacheCosinesX.evictAll()
        cacheCosinesY.evictAll()
    }

    private fun computeY(
        height: Int,
        numCompY: Int,
    ) = DoubleArray(height * numCompY) {
        val y = it / numCompY
        val j = it % numCompY
        cos(PI * y * j / height)
    }

    private fun computeX(
        width: Int,
        numCompX: Int,
    ) = DoubleArray(width * numCompX) {
        val x = it / numCompX
        val i = it % numCompX
        cos(PI * x * i / width)
    }

    /**
     * The Y cosine table for ([height], [numCompY]). With [useCache] the lookup
     * and the insert are one operation, so a concurrent decoder evicting the
     * entry between a has()/get() pair can no longer produce a null.
     */
    fun getArrayForCosinesY(
        useCache: Boolean,
        height: Int,
        numCompY: Int,
    ): DoubleArray {
        if (!useCache) return computeY(height, numCompY)
        val k = key(height, numCompY)
        return cacheCosinesY[k] ?: computeY(height, numCompY).also { cacheCosinesY.put(k, it) }
    }

    fun getArrayForCosinesX(
        useCache: Boolean,
        width: Int,
        numCompX: Int,
    ): DoubleArray {
        if (!useCache) return computeX(width, numCompX)
        val k = key(width, numCompX)
        return cacheCosinesX[k] ?: computeX(width, numCompX).also { cacheCosinesX.put(k, it) }
    }
}
