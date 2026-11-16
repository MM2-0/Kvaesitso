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
package com.vitorpamplona.amethyst.commons.robohash

import androidx.collection.LruCache
import androidx.compose.ui.graphics.vector.ImageVector

object CachedRobohash {
    private var cacheIsForLightTheme: Boolean? = null
    private val cache = LruCache<String, ImageVector>(100)
    private val assembler = RobohashAssembler()

    fun cached(
        msg: String,
        isLightTheme: Boolean,
    ): ImageVector? {
        // resets cache on theme change
        if (cacheIsForLightTheme != null && cacheIsForLightTheme != isLightTheme) {
            cache.evictAll()
        }

        return cache[msg]
    }

    fun trimToSize(maxItems: Int) {
        cache.trimToSize(maxItems)
    }

    fun get(
        msg: String,
        isLightTheme: Boolean,
    ): ImageVector {
        // Guard: empty/blank keys produce meaningless robohash; use a fallback hex
        val key = msg.ifBlank { "0000000000000000000000000000000000000000000000000000000000000000" }

        // resets cache on theme change
        if (cacheIsForLightTheme == null) {
            cacheIsForLightTheme = isLightTheme
        } else if (cacheIsForLightTheme != isLightTheme) {
            cacheIsForLightTheme = isLightTheme
            cache.evictAll()
        }

        cache[key]?.let {
            return it
        }

        val vector = assembler.build(key, isLightTheme)

        cache.put(key, vector)

        return vector
    }
}
