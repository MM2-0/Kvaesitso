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

import com.vitorpamplona.amethyst.commons.search.calendar.DateField
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.collections.immutable.toImmutableList

/**
 * The search field's own small language, scanned in one place: `from:`/`to:` (a person, an event
 * or an address), `since:`/`until:`, `#hashtag`, `label:<mark>`, `group:<id>`, `kind:`, `lang:`,
 * `domain:`, `-term`, `"a phrase"` and the NIP-73 scopes (`site:`, `isbn:`, `geo:`, `isan:`,
 * `doi:`, `podcast:*`).
 *
 * All of these become NIP-01 filter fields, never NIP-50 extensions, so they compose with a
 * relay's own ranking instead of competing with it. The field renderer and the filter builder
 * both take their token boundaries from here, so a chip can never claim a filter the query does
 * not send, nor the reverse.
 *
 * This is a hand-written scanner rather than one big regex: token boundaries here are
 * hand-tuned (a `to:` inside a url is not a filter; a pointer ends where the bech32 alphabet
 * stops), and the scanner keeps each of those decisions readable and separately testable.
 */
object SearchTokenizer {
    private const val BECH32 = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /** An npub is a fixed 63 characters, so its boundary is known before the checksum is read. */
    private const val NPUB_LENGTH = 63

    private val POINTER_PREFIXES = listOf("nevent1", "naddr1", "note1")

    /** The kind numbers a NIP-01 filter can carry, so `kind:99999` stays a search term. */
    private val KIND_RANGE = 0..65535

    /** An ISO 639 code with an optional region: `en`, `pt-BR`, `zh-Hant`. */
    private val LANGUAGE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?$")

    /** A bare hostname. A scheme or a path belongs to `site:`, which asks a different tag. */
    private val HOSTNAME = Regex("^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$")

    // The token types that only pill once the caret has left them: while a word is half-typed,
    // a chip forming under the caret would move the text out from under it.
    private val SETTLES =
        setOf(
            SearchSegment.Hashtag::class,
            SearchSegment.DateBound::class,
            SearchSegment.Scope::class,
            SearchSegment.Group::class,
            SearchSegment.Label::class,
            SearchSegment.Kind::class,
            SearchSegment.Language::class,
            SearchSegment.Domain::class,
            SearchSegment.Exclusion::class,
            SearchSegment.Phrase::class,
        )

    private fun isBech32(c: Char) = c in BECH32

    private val MARKS =
        setOf(
            CharCategory.NON_SPACING_MARK,
            CharCategory.COMBINING_SPACING_MARK,
            CharCategory.ENCLOSING_MARK,
        )

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c.category in MARKS

    /** The characters a hashtag body may not contain — the same set the note parser rejects. */
    private const val TAG_STOP = " \t\n\r!@#$%^&*()=+./,[{]};:'\"?><"

    /**
     * Punctuation a lifted token strands (`#bitcoin.` leaves `.`), which the leftover terms drop.
     * `#`, `"` and `-` are absent on purpose: those are NIP-50's own operators.
     */
    private const val ORPHAN = ".,;:!?()[]{}<>/\\|@$%^&*=+~'"

    /**
     * The typed string as segments, in order and covering every character: plain text, and the
     * tokens lifted out of it. Concatenating each segment's raw text reproduces the input exactly.
     */
    fun tokenize(text: String?): List<SearchSegment> {
        val s = text ?: return emptyList()
        if (s.isEmpty()) return emptyList()

        val out = mutableListOf<SearchSegment>()
        var textStart = 0
        var i = 0
        while (i < s.length) {
            if (!atWordStart(s, i)) {
                i++
                continue
            }
            val token = tokenAt(s, i)
            if (token == null) {
                i++
                continue
            }
            if (i > textStart) splitHashtags(s.substring(textStart, i), out)
            out.add(token)
            i += token.length
            textStart = i
        }
        if (textStart < s.length) splitHashtags(s.substring(textStart), out)
        return out
    }

    /**
     * The segments to draw: [tokenize]'s, minus the settling token the caret is inside, which
     * stays plain text until the caret leaves it. A null [caret] draws everything, which is what
     * a restore, a paste or an unfocused field wants.
     */
    fun drawable(
        text: String?,
        caret: Int?,
    ): List<SearchSegment> {
        val segments = tokenize(text)
        if (caret == null) return segments
        var at = 0
        return segments.map { seg ->
            val start = at
            at += seg.length
            if (seg::class !in SETTLES || caret <= start || caret > at) seg else SearchSegment.Text(seg.rawText)
        }
    }

    /** The leftover words, with the punctuation a lifted token stranded removed. */
    fun tidyTerms(text: String): String =
        text
            .split(' ', '\t', '\n', '\r')
            .filter { it.isNotEmpty() && !it.all { c -> c in ORPHAN } }
            .joinToString(" ")

    // ---- one token at one position -------------------------------------------------------

    private fun atWordStart(
        s: String,
        i: Int,
    ) = i == 0 || s[i - 1].isWhitespace()

    private fun tokenAt(
        s: String,
        i: Int,
    ): SearchSegment? =
        keyAt(s, i)
            ?: pointerAt(s, i)
            ?: dateAt(s, i)
            ?: scopeAt(s, i)
            ?: labelAt(s, i)
            ?: groupAt(s, i)
            ?: kindAt(s, i)
            ?: languageAt(s, i)
            ?: domainAt(s, i)
            ?: exclusionAt(s, i)
            ?: phraseAt(s, i)

    /** `from:<npub>`, `to:<npub>`, or a bare `npub1…` that stays a search term. */
    private fun keyAt(
        s: String,
        i: Int,
    ): SearchSegment.Key? {
        val field = prefixAt(s, i, listOf("from:", "to:"))
        val keyStart = i + (field?.length ?: 0)
        if (!s.startsWith("npub1", keyStart)) return null
        if (keyStart + NPUB_LENGTH > s.length) return null
        val npub = s.substring(keyStart, keyStart + NPUB_LENGTH)
        if (!npub.drop(5).all(::isBech32)) return null
        // A longer bech32 run is a different string, not an npub with a suffix.
        val after = keyStart + NPUB_LENGTH
        if (after < s.length && (s[after].isLetterOrDigit())) return null
        val pubkey =
            when (val entity = Nip19Parser.uriToRoute(npub)?.entity) {
                is NPub -> entity.hex
                is NProfile -> entity.hex
                else -> null
            } ?: return null
        return SearchSegment.Key(
            raw = s.substring(i, after),
            field = field?.let { KeyField.of(it.dropLast(1)) },
            pubkey = pubkey,
        )
    }

    /** `to:<note|nevent|naddr>` — the other two things a `to:` can name. */
    private fun pointerAt(
        s: String,
        i: Int,
    ): SearchSegment.Pointer? {
        if (!s.regionMatches(i, "to:", 0, 3, ignoreCase = true)) return null
        val start = i + 3
        val prefix = POINTER_PREFIXES.firstOrNull { s.startsWith(it, start, ignoreCase = true) } ?: return null
        var end = start + prefix.length
        while (end < s.length && isBech32(s[end])) end++
        if (end < s.length && s[end].isLetterOrDigit()) return null
        val raw = s.substring(start, end)
        val (tag, value) =
            when (val entity = Nip19Parser.uriToRoute(raw)?.entity) {
                is NNote -> "e" to entity.hex
                is NEvent -> "e" to entity.hex
                is NAddress -> "a" to entity.aTag()
                else -> return null
            }
        return SearchSegment.Pointer(raw = s.substring(i, end), tag = tag, value = value)
    }

    /** `since:YYYY-MM-DD` / `until:YYYY-MM-DD`, resolved to the unix second that bound means. */
    private fun dateAt(
        s: String,
        i: Int,
    ): SearchSegment.DateBound? {
        val prefix = prefixAt(s, i, listOf("since:", "until:")) ?: return null
        val field = DateField.of(prefix.dropLast(1)) ?: return null
        val start = i + prefix.length
        if (start + 10 > s.length) return null
        val ymd = s.substring(start, start + 10)
        val date = SearchDate.parse(ymd) ?: return null
        // A date ends on anything but a word character or a hyphen, so `2026-04-01-part2` is text.
        val after = start + 10
        if (after < s.length && (isWordChar(s[after]) || s[after] == '-')) return null
        return SearchSegment.DateBound(
            raw = s.substring(i, after),
            field = field,
            date = date,
            at = if (field == DateField.SINCE) LocalClock.startOfDay(date) else LocalClock.endOfDay(date),
        )
    }

    /** A NIP-73 scope: `site:`, `isbn:`, `geo:`, `isan:`, `doi:`, `podcast:*`. */
    private fun scopeAt(
        s: String,
        i: Int,
    ): SearchSegment.Scope? {
        val prefix = prefixAt(s, i, ScopeIds.PREFIXES.map { "$it:" }) ?: return null
        val field = prefix.dropLast(1).lowercase()
        val value = valueAt(s, i + prefix.length) ?: return null
        // A scope with no askable id (`site:#top`) is not a token: its chip would claim a filter
        // that the builder would not send.
        if (ScopeIds.scopeIds(field, value).isEmpty()) return null
        return SearchSegment.Scope(raw = s.substring(i, i + prefix.length + value.length), field = field, value = value)
    }

    /** `label:<mark>` — a NIP-32 mark from its namespace (`review/app`, `en`). */
    private fun labelAt(
        s: String,
        i: Int,
    ): SearchSegment.Label? {
        val prefix = prefixAt(s, i, listOf("label:")) ?: return null
        val value = valueAt(s, i + prefix.length) ?: return null
        return SearchSegment.Label(raw = s.substring(i, i + prefix.length + value.length), value = value)
    }

    /** `group:<id>` — whatever id the host relay minted, delimited like a scope's value. */
    private fun groupAt(
        s: String,
        i: Int,
    ): SearchSegment.Group? {
        val prefix = prefixAt(s, i, listOf("group:")) ?: return null
        val value = valueAt(s, i + prefix.length) ?: return null
        return SearchSegment.Group(raw = s.substring(i, i + prefix.length + value.length), id = value)
    }

    /**
     * `kind:<alias|number>` — an alias the registry knows, one of the pseudo-kinds, or a plain
     * kind number.
     *
     * Anything else is deliberately not a token. `kind:banana` resolves to no window at all, and
     * [QueryParser]'s second pass already drops it back into the search terms; a chip over it
     * would be the field claiming a filter the REQ never carries.
     */
    private fun kindAt(
        s: String,
        i: Int,
    ): SearchSegment.Kind? {
        val prefix = prefixAt(s, i, listOf("kind:")) ?: return null
        val value = valueAt(s, i + prefix.length) ?: return null
        val raw = s.substring(i, i + prefix.length + value.length)

        if (KindRegistry.isPseudoKind(value)) {
            return SearchSegment.Kind(raw = raw, alias = value.lowercase(), pseudoKind = value.lowercase())
        }
        val resolved = KindRegistry.resolve(value) ?: value.toIntOrNull()?.takeIf { it in KIND_RANGE }?.let { listOf(it) } ?: return null
        return SearchSegment.Kind(raw = raw, alias = value.lowercase(), kinds = resolved.toImmutableList())
    }

    /** `lang:<code>` — an ISO 639 code, optionally with a region (`pt`, `pt-BR`). */
    private fun languageAt(
        s: String,
        i: Int,
    ): SearchSegment.Language? {
        val prefix = prefixAt(s, i, listOf("lang:")) ?: return null
        val value = valueAt(s, i + prefix.length) ?: return null
        if (!LANGUAGE.matches(value)) return null
        return SearchSegment.Language(raw = s.substring(i, i + prefix.length + value.length), code = value.lowercase())
    }

    /** `domain:<host>` — a bare hostname, which is the only spelling NIP-50 takes. */
    private fun domainAt(
        s: String,
        i: Int,
    ): SearchSegment.Domain? {
        val prefix = prefixAt(s, i, listOf("domain:")) ?: return null
        val value = valueAt(s, i + prefix.length) ?: return null
        if (!HOSTNAME.matches(value)) return null
        return SearchSegment.Domain(raw = s.substring(i, i + prefix.length + value.length), host = value.lowercase())
    }

    /**
     * `-term` — read exactly as [QueryParser]'s own negation scanner reads it, because that is
     * still the pass that turns it into a filter. A `-` with nothing after it is not an
     * exclusion, and neither is a bare `-` before a space.
     *
     * `-#tag` is left alone deliberately: the hashtag splitter already claims it, and taking it
     * here would quietly turn "the bitcoin hashtag" into "not the word #bitcoin". Changing that
     * is a decision about the language, not about how it is drawn.
     */
    private fun exclusionAt(
        s: String,
        i: Int,
    ): SearchSegment.Exclusion? {
        if (s[i] != '-') return null
        val start = i + 1
        if (start >= s.length || s[start].isWhitespace() || s[start] == '#') return null
        var end = start
        while (end < s.length && !s[end].isWhitespace()) end++
        return SearchSegment.Exclusion(raw = s.substring(i, end), term = s.substring(start, end))
    }

    /**
     * `"a phrase"`, including an unterminated one, which [QueryParser] reads to the end of the
     * input — so the chip covers the same characters the filter does.
     */
    private fun phraseAt(
        s: String,
        i: Int,
    ): SearchSegment.Phrase? {
        if (s[i] != '"') return null
        var end = i + 1
        while (end < s.length && s[end] != '"') end++
        val text = s.substring(i + 1, end)
        if (end < s.length) end++
        return SearchSegment.Phrase(raw = s.substring(i, end), text = text)
    }

    private fun prefixAt(
        s: String,
        i: Int,
        options: List<String>,
    ): String? = options.firstOrNull { s.regionMatches(i, it, 0, it.length, ignoreCase = true) }?.let { s.substring(i, i + it.length) }

    /**
     * A prefixed token's value: everything to the next whitespace, minus trailing sentence
     * punctuation. Only `. , ; ! ?`, since DOIs and urls contain nearly anything else.
     */
    private fun valueAt(
        s: String,
        start: Int,
    ): String? {
        var end = start
        while (end < s.length && !s[end].isWhitespace()) end++
        while (end > start && s[end - 1] in ".,;!?") end--
        return if (end > start) s.substring(start, end) else null
    }

    // ---- hashtags inside the leftover text ------------------------------------------------

    /**
     * The hashtags inside one stretch of plain text, as segments in place. A `#` only opens a tag
     * at a word start, so the fragment in a url is not one.
     */
    private fun splitHashtags(
        chunk: String,
        out: MutableList<SearchSegment>,
    ) {
        var at = 0
        var i = 0
        while (i < chunk.length) {
            if (chunk[i] != '#' || (i > 0 && isWordChar(chunk[i - 1]))) {
                i++
                continue
            }
            var end = i + 1
            while (end < chunk.length && chunk[end] !in TAG_STOP) end++
            if (end == i + 1) {
                i++
                continue
            }
            val raw = chunk.substring(i, end)
            // A tag that is nothing but hyphens normalizes to empty and is not a tag.
            val tag = raw.drop(1).trimEnd('-').lowercase()
            if (tag.isEmpty()) {
                i = end
                continue
            }
            if (i > at) out.add(SearchSegment.Text(chunk.substring(at, i)))
            // `raw` keeps a trailing hyphen, or the chip would cover fewer characters than it stands for.
            out.add(SearchSegment.Hashtag(raw = raw, tag = tag))
            at = end
            i = end
        }
        if (at < chunk.length) out.add(SearchSegment.Text(chunk.substring(at)))
    }
}
