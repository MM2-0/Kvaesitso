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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.quartz.experimental.publications.AsciiDocToMarkdown
import com.vitorpamplona.quartz.utils.cache.ConcurrentLruCache

/**
 * The shared cache in front of [AsciiDocToMarkdown], for the same reason [CachedRichTextParser]
 * exists: the conversion runs on the composition thread, and `remember` is dropped the moment a
 * card scrolls out of a lazy list, so without this a reader scrolling a publication back and
 * forth re-converts the same chapter every time it returns.
 *
 * It is not a cheap call at chapter size. Measured over 638 kind-30041 sections taken off the
 * public relays (1.9 MB of prose), on a desktop JVM: p50 114us, p95 1.4ms, worst 7.2ms for a
 * 13.5 KB section. A phone is several times slower than that, which puts the tail well past a
 * frame.
 *
 * Keyed by event id because a section's body is immutable for a given id -- an edited chapter is
 * a new event with a new id, and so a new entry.
 */
object CachedAsciiDocToMarkdown {
    // Sized for a publication's visible chapters plus the ones just scrolled past, not a whole
    // book: each entry is one converted chapter, single-digit KB of text.
    private val cache = ConcurrentLruCache<String, String>(60)

    fun convert(
        eventId: String,
        content: String,
        resolveWikilink: (String) -> String?,
    ): String = cache.get(eventId) ?: AsciiDocToMarkdown.convert(content, resolveWikilink).also { cache.put(eventId, it) }

    fun trimToSize(maxItems: Int) = cache.trimToSize(maxItems)
}
