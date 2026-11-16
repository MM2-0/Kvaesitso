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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable

@Stable
class ImmutableListOfLists<T>(
    val lists: Array<Array<T>>,
) {
    // Memoized content hash of all tags. `lists` is an Array (identity hashCode),
    // so any consumer that needs a *content*-based key (e.g. CachedRichTextParser,
    // so an off-thread pre-parse maps to the same cache entry the renderer uses)
    // would otherwise re-traverse every tag on each call. Computing it once per
    // instance keeps that O(1)-amortized. Benign data race: concurrent first
    // readers just recompute the same value.
    private var cachedContentHash: Int? = null

    fun contentHash(): Int = cachedContentHash ?: lists.contentDeepHashCode().also { cachedContentHash = it }
}

val EmptyTagList = ImmutableListOfLists<String>(emptyArray())

fun Array<Array<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> = ImmutableListOfLists(this)
