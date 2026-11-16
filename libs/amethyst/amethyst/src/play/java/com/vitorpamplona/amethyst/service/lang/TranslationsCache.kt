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
package com.vitorpamplona.amethyst.service.lang

import android.util.LruCache
import com.vitorpamplona.amethyst.commons.ui.components.TranslationConfig

object TranslationsCache {
    private const val MAX_ENTRIES = 500

    // Keying on the language settings as well prevents serving stale translations after the user
    // changes "Translate to" or "Don't translate from".
    private data class Key(
        val content: String,
        val translateTo: String,
        val dontTranslateFrom: Set<String>,
    )

    private val cache = LruCache<Key, TranslationConfig>(MAX_ENTRIES)

    fun get(
        content: String,
        translateTo: String,
        dontTranslateFrom: Set<String>,
    ): TranslationConfig? = cache.get(Key(content, translateTo, dontTranslateFrom))

    fun set(
        content: String,
        translateTo: String,
        dontTranslateFrom: Set<String>,
        config: TranslationConfig,
    ) {
        cache.put(Key(content, translateTo, dontTranslateFrom), config)
    }

    fun clear() {
        cache.evictAll()
    }
}
