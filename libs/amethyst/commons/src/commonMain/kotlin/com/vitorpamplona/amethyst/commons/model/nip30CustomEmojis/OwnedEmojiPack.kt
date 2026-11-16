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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

@Stable
data class OwnedEmojiPack(
    val identifier: String,
    val title: String,
    val description: String?,
    val image: String?,
    val publicEmojis: List<EmojiUrlTag> = emptyList(),
    val privateEmojis: List<EmojiUrlTag> = emptyList(),
) {
    val totalEmojis: Int get() = publicEmojis.size + privateEmojis.size

    fun containsShortcode(shortcode: String): Boolean =
        publicEmojis.any { it.code == shortcode } ||
            privateEmojis.any { it.code == shortcode }
}
