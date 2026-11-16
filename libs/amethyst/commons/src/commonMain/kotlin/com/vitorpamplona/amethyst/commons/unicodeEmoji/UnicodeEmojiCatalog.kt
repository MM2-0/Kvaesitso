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

/** The skin tones an emoji palette offers, in Unicode's order. [NONE] is the default yellow. */
enum class SkinTone {
    NONE,
    LIGHT,
    MEDIUM_LIGHT,
    MEDIUM,
    MEDIUM_DARK,
    DARK,
}

/**
 * One palette entry: the fully-qualified [emoji], its Unicode CLDR [name] and, for emoji that
 * take one, the five uniform skin-tone variants in [SkinTone] order (light to dark).
 */
@Immutable
class UnicodeEmoji(
    val emoji: String,
    val name: String,
    val skinTones: List<String>? = null,
) {
    /** [name] lowercased once, so a search keystroke does not lowercase the whole palette again. */
    internal val searchName = name.lowercase()

    fun withTone(tone: SkinTone): String = if (tone == SkinTone.NONE || skinTones == null) emoji else skinTones[tone.ordinal - 1]
}

/** A Unicode emoji group ("Smileys & Emotion", "Flags", ...), named as in emoji-test.txt. */
@Immutable
class UnicodeEmojiGroup(
    val name: String,
    val emojis: List<UnicodeEmoji>,
)

/**
 * The Unicode emoji palette, in CLDR keyboard order. It feeds the picker on platforms without a
 * system emoji keyboard (Desktop). Build it with [parse] from the file
 * `tools/emoji-data/generate.py` writes.
 */
@Immutable
class UnicodeEmojiCatalog(
    val groups: List<UnicodeEmojiGroup>,
) {
    private val byEmoji: Map<String, UnicodeEmoji> by lazy {
        val map = HashMap<String, UnicodeEmoji>()
        groups.forEach { group ->
            group.emojis.forEach { entry ->
                map[entry.emoji] = entry
                entry.skinTones?.forEach { map[it] = entry }
            }
        }
        map
    }

    /** The entry for [emoji], or for the base emoji of one of its skin-tone variants. */
    fun find(emoji: String): UnicodeEmoji? = byEmoji[emoji]

    /**
     * The emoji whose name holds every word of [query]: an exact name first, then names starting
     * with the query, then names with a word starting with it, then the rest; palette order within
     * each.
     */
    fun search(query: String): List<UnicodeEmoji> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val words = needle.split(' ').filter { it.isNotEmpty() }
        val wordStart = " $needle"

        val exact = mutableListOf<UnicodeEmoji>()
        val starts = mutableListOf<UnicodeEmoji>()
        val wordStarts = mutableListOf<UnicodeEmoji>()
        val contains = mutableListOf<UnicodeEmoji>()
        groups.forEach { group ->
            group.emojis.forEach { entry ->
                val name = entry.searchName
                if (words.all { name.contains(it) }) {
                    when {
                        name == needle -> exact.add(entry)
                        name.startsWith(needle) -> starts.add(entry)
                        name.contains(wordStart) -> wordStarts.add(entry)
                        else -> contains.add(entry)
                    }
                }
            }
        }
        return exact + starts + wordStarts + contains
    }

    companion object {
        /**
         * Parses the palette file: `#` lines are comments, `@<name>` opens a group, and each other
         * line is `<emoji>\t<name>[\t<five skin-tone variants, space separated>]`.
         */
        fun parse(text: String): UnicodeEmojiCatalog {
            val groups = mutableListOf<UnicodeEmojiGroup>()
            var name: String? = null
            var emojis = mutableListOf<UnicodeEmoji>()

            fun closeGroup() {
                name?.let { if (emojis.isNotEmpty()) groups.add(UnicodeEmojiGroup(it, emojis)) }
            }

            text.lineSequence().forEach { line ->
                when {
                    line.isBlank() || line.startsWith('#') -> {}

                    line.startsWith('@') -> {
                        closeGroup()
                        name = line.substring(1).trim()
                        emojis = mutableListOf()
                    }

                    name != null -> {
                        val fields = line.split('\t')
                        if (fields.size >= 2) {
                            val tones = fields.getOrNull(2)?.split(' ')?.takeIf { it.size == SkinTone.entries.size - 1 }
                            emojis.add(UnicodeEmoji(fields[0], fields[1], tones))
                        }
                    }
                }
            }
            closeGroup()
            return UnicodeEmojiCatalog(groups)
        }
    }
}
