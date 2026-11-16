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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class CosineCacheTest {
    @Test
    fun sameProductDifferentShapeGetsDifferentTables() {
        CosineCache.clearCache()
        // (50, 4) and (100, 2) both have 200 entries; the old product key served
        // whichever was computed first to both.
        val a = CosineCache.getArrayForCosinesX(useCache = true, width = 50, numCompX = 4)
        val b = CosineCache.getArrayForCosinesX(useCache = true, width = 100, numCompX = 2)
        assertFalse(a.contentEquals(b))
        assertContentEquals(CosineCache.getArrayForCosinesX(useCache = false, width = 100, numCompX = 2), b)
    }
}
