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
package com.vitorpamplona.amethyst.commons.unicodeEmoji.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.unicodeEmoji.SkinTone
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * The Unicode emoji palette bundled in compose resources (`tools/emoji-data/generate.py` writes
 * it), parsed once per process, plus the picker choices that last for the session.
 */
object UnicodeEmojiPalette {
    const val PATH = "files/emoji/emoji.tsv"

    @Volatile
    private var cached: UnicodeEmojiCatalog? = null

    fun cachedOrNull(): UnicodeEmojiCatalog? = cached

    suspend fun load(): UnicodeEmojiCatalog =
        cached ?: withContext(Dispatchers.Default) {
            UnicodeEmojiCatalog.parse(Res.readBytes(PATH).decodeToString())
        }.also { cached = it }

    /** The skin tone last picked in the palette, applied to every emoji that takes one. */
    var skinTone by mutableStateOf(SkinTone.NONE)
}
