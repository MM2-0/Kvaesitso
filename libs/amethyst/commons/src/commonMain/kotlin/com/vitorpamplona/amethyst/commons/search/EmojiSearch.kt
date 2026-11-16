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
package com.vitorpamplona.amethyst.commons.search

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip50Search.EventSearchMatcher
import com.vitorpamplona.quartz.nip50Search.SearchQuery as Nip50Query

/** One emoji a search found, and the pack (kind 30030) it came from. */
@Immutable
data class EmojiSearchHit(
    val emoji: EmojiUrlTag,
    val pack: Note,
)

/**
 * Pulls the single emojis a search asked for out of the emoji packs it found.
 *
 * A pack matches a search when its title, description or any shortcode holds the terms (Quartz's
 * `EventSearchMatcher`), so typing `cat` returns every pack with a `:blobcat:` in it, as a whole
 * pack. What the reader wanted is the cat. This takes the found packs and keeps the emojis whose
 * own shortcode holds every term, by the same rule the pack was matched by: a case-insensitive
 * substring. A pack matched only by its title gives no emojis here; its card still shows.
 */
object EmojiSearch {
    /** The most emojis one search hands back: a screenful of tiles, not a pack dump. */
    const val MAX_HITS = 120

    /**
     * What a shortcode must hold to match: every one of [all], at least one of [any] when there is
     * an `OR` chain, and none of [none]. All lowercased shortcode fragments.
     */
    @Immutable
    data class Terms(
        val all: List<String> = emptyList(),
        val any: List<String> = emptyList(),
        val none: List<String> = emptyList(),
    ) {
        /** No free text: a bare `kind:emoji` asks for packs, not for emojis. */
        val isEmpty: Boolean get() = all.isEmpty() && any.isEmpty()

        /** The fragment a best match starts with. */
        val lead: String? get() = all.firstOrNull() ?: any.firstOrNull()

        companion object {
            val EMPTY = Terms()
        }
    }

    /** A fragment as a shortcode would hold it: lowercased, with the `:` a reader types around one. */
    private fun fragment(text: String): String? =
        text
            .trim()
            .trim(':')
            .lowercase()
            .takeIf { it.isNotEmpty() }

    /** The words of a quoted phrase: `"blob cat"` matches `blobcat` and `blob_cat` alike. */
    private fun words(phrase: String): List<String> {
        val out = ArrayList<String>()
        val word = StringBuilder()
        for (c in phrase) {
            if (c.isLetterOrDigit()) {
                word.append(c.lowercaseChar())
            } else if (word.isNotEmpty()) {
                out.add(word.toString())
                word.clear()
            }
        }
        if (word.isNotEmpty()) out.add(word.toString())
        return out
    }

    /** The [query]'s free text, phrases, `OR` chain and exclusions as shortcode fragments. */
    fun terms(query: SearchQuery): Terms = terms(query.text, query.orTerms, query.excludeTerms)

    fun terms(
        text: String,
        orTerms: List<String> = emptyList(),
        excludeTerms: List<String> = emptyList(),
    ): Terms {
        val parsed = if (text.isBlank()) null else Nip50Query.parse(text)
        val all =
            (
                EventSearchMatcher.splitTerms(parsed?.terms).mapNotNull(::fragment) +
                    parsed?.phrases.orEmpty().flatMap(::words)
            ).distinct()
        val any = orTerms.mapNotNull(::fragment).distinct()
        val none =
            (
                excludeTerms.mapNotNull(::fragment) +
                    parsed?.notTerms.orEmpty().mapNotNull(::fragment) +
                    parsed?.notPhrases.orEmpty().flatMap(::words)
            ).distinct()
        return if (all.isEmpty() && any.isEmpty()) Terms.EMPTY else Terms(all, any, none)
    }

    /** True when [code] holds every required fragment, one of the `OR` ones, and no excluded one. */
    fun matches(
        code: String,
        terms: Terms,
    ): Boolean {
        if (terms.isEmpty) return false
        if (!terms.all.all { code.contains(it, ignoreCase = true) }) return false
        if (terms.any.isNotEmpty() && terms.any.none { code.contains(it, ignoreCase = true) }) return false
        return terms.none.none { code.contains(it, ignoreCase = true) }
    }

    /**
     * The emojis in [notes] (only the emoji packs among them count) that [matches] [terms], one per
     * image, best first: a shortcode that is the lead fragment exactly, then one that starts with
     * it, then the rest, each in the order the packs were found.
     */
    fun find(
        notes: List<Note>,
        terms: Terms,
        max: Int = MAX_HITS,
    ): List<EmojiSearchHit> {
        val lead = terms.lead ?: return emptyList()
        val single = terms.all.size + terms.any.size == 1

        val exact = ArrayList<EmojiSearchHit>()
        val prefix = ArrayList<EmojiSearchHit>()
        val rest = ArrayList<EmojiSearchHit>()
        val seenUrls = HashSet<String>()

        for (note in notes) {
            val pack = note.event as? EmojiPackEvent ?: continue
            for (emoji in pack.publicEmojis()) {
                if (!matches(emoji.code, terms) || !seenUrls.add(emoji.url)) continue
                val hit = EmojiSearchHit(emoji, note)
                when {
                    single && emoji.code.equals(lead, ignoreCase = true) -> exact.add(hit)
                    emoji.code.startsWith(lead, ignoreCase = true) -> prefix.add(hit)
                    else -> rest.add(hit)
                }
            }
        }

        val all = ArrayList<EmojiSearchHit>(exact.size + prefix.size + rest.size)
        all.addAll(exact)
        all.addAll(prefix)
        all.addAll(rest)
        return if (all.size > max) all.subList(0, max) else all
    }
}
