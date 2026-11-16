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
package com.vitorpamplona.amethyst.commons.unicodeEmoji

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.util.firstFullChar
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

private const val VARIATION_SELECTOR = '\uFE0F'

private fun String.withoutVariationSelector() = if (VARIATION_SELECTOR in this) replace(VARIATION_SELECTOR.toString(), "") else this

/** A saved bare `:shortcode:` (its event had no emoji tag, or another client wrote it). */
private fun String.bareShortcodeOrNull(): String? = if (length > 2 && startsWith(':') && endsWith(':') && indexOf(':', 1) == length - 1) substring(1, length - 1) else null

/**
 * A set of chosen emojis, such as the account's reaction choices, as an emoji picker asks about it:
 * is this emoji in the set, and in which form was it saved?
 *
 * The saved form matters because a choice may be stored in a form the picker does not draw: in
 * another skin tone, without the variation selector (`❤` for the palette's `❤` plus U+FE0F), or a custom emoji
 * under another shortcode, or as a bare `:shortcode:`. Matching only the exact string left those
 * unmarked, and a tap added a near-duplicate instead of removing the choice.
 */
@Immutable
class EmojiChoices private constructor(
    private val unicode: Set<String>,
    private val unicodeByBase: Map<String, String>,
    private val customLinks: Set<String>,
    private val bareShortcodes: Set<String>,
) {
    /** The saved form of [emoji], or null when it is not chosen. */
    fun savedForm(emoji: String): String? = if (emoji in unicode) emoji else unicodeByBase[emoji.withoutVariationSelector()]

    /** The saved form of [entry] in [tone], else in any tone, or null when no form is chosen. */
    fun savedForm(
        entry: UnicodeEmoji,
        tone: SkinTone,
    ): String? {
        savedForm(entry.withTone(tone))?.let { return it }
        savedForm(entry.emoji)?.let { return it }
        entry.skinTones?.forEach { variant -> savedForm(variant)?.let { return it } }
        return null
    }

    /**
     * The saved form of [emoji], an emoji as typed rather than drawn from the palette, trying its
     * palette [entry] in every tone when the exact form is not chosen: a typed 👍 finds a saved 👍🏽.
     */
    fun savedForm(
        emoji: String,
        entry: UnicodeEmoji?,
    ): String? = savedForm(emoji) ?: entry?.let { savedForm(it, SkinTone.NONE) }

    /** Whether [media] is chosen, by its image, or by its code when saved without one. */
    fun contains(media: EmojiMedia): Boolean = media.link in customLinks || media.code in bareShortcodes

    companion object {
        fun of(choices: List<String>): EmojiChoices {
            val unicode = HashSet<String>()
            val byBase = HashMap<String, String>()
            val links = HashSet<String>()
            val bare = HashSet<String>()
            choices.forEach { choice ->
                val link = choice.customEmojiUrlOrNull()
                val code = if (link == null) choice.bareShortcodeOrNull() else null
                when {
                    link != null -> links.add(link)
                    code != null -> bare.add(code)
                    else -> {
                        unicode.add(choice)
                        byBase.getOrPut(choice.withoutVariationSelector()) { choice }
                    }
                }
            }
            return EmojiChoices(unicode, byBase, links, bare)
        }
    }
}

/**
 * These choices with [emoji] removed when present (every copy, as older versions could save one
 * twice), else added. A picker hands over the saved form of a chosen one.
 */
fun List<String>.toggleEmoji(emoji: String): List<String> =
    when {
        emoji.isBlank() -> this
        emoji in this -> filter { it != emoji }
        else -> this + emoji
    }

/** These choices with [media] removed (in every saved form) when present, else added as `:code:url`. */
fun List<String>.toggleEmoji(media: EmojiMedia): List<String> {
    val saved = filter { it.customEmojiUrlOrNull() == media.link || it.bareShortcodeOrNull() == media.code }
    return if (saved.isNotEmpty()) this - saved.toSet() else this + EmojiUrlTag(media.code, media.link).encode()
}

/**
 * Text typed into an emoji search as an emoji of its own, when it is not a name to look up: an
 * EmojiCoder message whole, a palette emoji whole (keycaps and tag flags span several characters),
 * else its first character unless that is a letter, a digit or a shortcode's colon.
 */
fun typedEmoji(
    text: String,
    catalog: UnicodeEmojiCatalog,
): String? {
    if (EmojiCoder.isCoded(text)) return EmojiCoder.cropToFirstMessage(text)
    if (catalog.find(text) != null) return text
    val first = text.firstFullChar()
    if (first.isEmpty() || first[0].isLetterOrDigit() || first[0] == ':') return null
    return first
}
