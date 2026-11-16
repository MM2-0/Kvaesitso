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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

sealed interface Token {
    data class Operator(
        val name: String,
        val value: String,
        val raw: String,
    ) : Token

    data class Text(
        val value: String,
    ) : Token

    data object Or : Token

    data class Quoted(
        val value: String,
        val raw: String,
    ) : Token

    data class Negation(
        val term: String,
    ) : Token
}

/** A second-pass [Token] with the half-open span [start, end) of the typed string it covers. */
internal data class PositionedToken(
    val token: Token,
    val start: Int,
    val end: Int,
)

/**
 * The typed string as a [SearchQuery], in two passes.
 *
 * [SearchTokenizer] runs first and lifts out everything that has a hard shape — a key, a NIP-19
 * pointer, an ISO day, a hashtag, a label, a scope, a group. Those are the tokens the field draws
 * as chips, so parsing them here rather than a second time is what keeps the chip and the filter
 * from ever disagreeing.
 *
 * What is left is plain text, and this second pass reads the looser operators out of it: the
 * `OR` chain, `-exclusions`, `"quoted phrases"`, and the spellings the tokenizer deliberately
 * refuses because they have no single unambiguous rendering as a chip — a hex `from:`, a bare
 * `since:` year, a `kind:` the registry cannot resolve, a `lang:`/`domain:` that is not one.
 * Those keep working as filters; they just do not pill.
 */
object QueryParser {
    private val KNOWN_OPERATORS = setOf("from", "to", "kind", "since", "until", "lang", "domain")

    fun parse(input: String): SearchQuery {
        if (input.isBlank()) return SearchQuery.EMPTY

        val segments = SearchTokenizer.tokenize(input)
        val builder = QueryBuilder()
        val leftover = StringBuilder()

        segments.forEach { seg ->
            when (seg) {
                is SearchSegment.Text -> leftover.append(seg.text)
                is SearchSegment.Hashtag -> builder.hashtags.addDistinct(seg.tag)
                is SearchSegment.Label -> builder.labels.addDistinct(seg.value)
                is SearchSegment.Group -> builder.groups.addDistinct(seg.id)
                is SearchSegment.Scope -> builder.scopes.addDistinct(ExternalScope(seg.field, seg.value))
                is SearchSegment.Pointer ->
                    if (seg.tag == "e") builder.cites.addDistinct(seg.value) else builder.addrs.addDistinct(seg.value)

                is SearchSegment.DateBound -> builder.narrow(seg.field, seg.at)
                is SearchSegment.Kind ->
                    if (seg.pseudoKind != null) {
                        builder.pseudoKinds.addDistinct(seg.pseudoKind)
                    } else {
                        seg.kinds.forEach { builder.kinds.addDistinct(it) }
                    }

                is SearchSegment.Language -> builder.language = seg.code
                is SearchSegment.Domain -> builder.domain = seg.host
                // Drawn only. These two exist so the field can chip them; the pass below still
                // owns what they mean, so their text goes through untouched rather than being
                // read twice in two places that could drift apart.
                is SearchSegment.Exclusion -> leftover.append(seg.raw)
                is SearchSegment.Phrase -> leftover.append(seg.raw)
                is SearchSegment.Key ->
                    when (seg.field) {
                        KeyField.FROM -> builder.authors.addDistinct(seg.pubkey)
                        KeyField.TO -> builder.mentions.addDistinct(seg.pubkey)
                        // A bare npub names nobody in particular; it stays a search term.
                        null -> leftover.append(seg.raw)
                    }
            }
        }

        readWords(tokenize(leftover.toString()), builder)
        return builder.build()
    }

    // ---- the second pass: the looser operators inside the leftover text --------------------

    internal fun tokenize(input: String): List<Token> = positionedTokens(input).map { it.token }

    /**
     * [tokenize], with the span of the typed string each token was read from.
     *
     * The parse itself never needs the offsets — a token carries its meaning, not where it
     * stood. What needs them is the in-place rewrite of an author qualifier
     * ([AuthorNameRewrite]), which splices a resolved from:npub1… over exactly the characters
     * the name occupied, and can only do that without re-reading the field if the same pass
     * that recognised the token says where it was.
     */
    internal fun positionedTokens(input: String): List<PositionedToken> {
        val tokens = mutableListOf<PositionedToken>()
        var i = 0
        val len = input.length

        while (i < len) {
            if (input[i].isWhitespace()) {
                i++
                continue
            }

            // Quoted phrase
            if (input[i] == '"') {
                val start = i
                i++ // skip opening quote
                val sb = StringBuilder()
                while (i < len && input[i] != '"') {
                    sb.append(input[i])
                    i++
                }
                if (i < len) i++ // skip closing quote
                tokens.add(PositionedToken(Token.Quoted(sb.toString(), input.substring(start, i)), start, i))
                continue
            }

            // Negation
            if (input[i] == '-' && i + 1 < len && !input[i + 1].isWhitespace()) {
                val start = i
                i++ // skip -
                val word = readWord(input, i)
                i += word.length
                if (word.isNotEmpty()) tokens.add(PositionedToken(Token.Negation(word), start, i))
                continue
            }

            val start = i
            val word = readWord(input, i)
            i += word.length

            if (word.isEmpty()) {
                i++
                continue
            }

            if (word == "OR") {
                tokens.add(PositionedToken(Token.Or, start, i))
                continue
            }

            val colonIdx = word.indexOf(':')
            if (colonIdx > 0) {
                val opName = word.substring(0, colonIdx).lowercase()
                val opValue = word.substring(colonIdx + 1)
                if (opName in KNOWN_OPERATORS && opValue.isNotEmpty()) {
                    tokens.add(PositionedToken(Token.Operator(opName, opValue, word), start, i))
                    continue
                }
                // Malformed operator (no value or unknown) → treat as text
            }

            tokens.add(PositionedToken(Token.Text(word), start, i))
        }

        return tokens
    }

    private fun readWord(
        input: String,
        start: Int,
    ): String {
        var i = start
        while (i < input.length && !input[i].isWhitespace()) i++
        return input.substring(start, i)
    }

    private fun readWords(
        tokens: List<Token>,
        builder: QueryBuilder,
    ) {
        var i = 0
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Operator -> builder.readOperator(token)

                is Token.Text -> {
                    // An OR chain: `a OR b OR c`, which becomes one NIP-50 alternation.
                    if (i + 2 < tokens.size && tokens[i + 1] is Token.Or && tokens[i + 2] is Token.Text) {
                        builder.orTerms.add(token.value)
                        i++ // step onto the OR
                        while (i < tokens.size && tokens[i] is Token.Or && i + 1 < tokens.size && tokens[i + 1] is Token.Text) {
                            i++ // skip OR
                            builder.orTerms.add((tokens[i] as Token.Text).value)
                            i++ // skip text
                        }
                        continue
                    }
                    builder.textParts.add(token.value)
                }

                is Token.Quoted -> builder.textParts.add(token.raw)
                is Token.Negation -> builder.excludeTerms.addDistinct(token.term)
                // An orphaned OR, with no text on one side of it, is just a word.
                is Token.Or -> builder.textParts.add("OR")
            }
            i++
        }
    }

    /**
     * The pubkey a `from:`/`to:` value names, or null when the value is a name instead — a
     * NIP-05 address, a `.bit` name, a display name for the picker.
     *
     * Strict on purpose: the bech32 spellings decode by their prefix, and a raw key counts only
     * as exactly 64 hex chars. [decodePublicKeyAsHexOrNull] cannot be asked this — its fallback
     * hex-decodes any even-length string, so `from:name.bit` or `from:d/alice` would come out
     * as noise nibbles and the name would filter by a garbage author nobody can ever match.
     */
    internal fun decodeQualifierPubkeyOrNull(value: String): HexKey? =
        if (value.length == 64 && Hex.isHex64(value)) {
            value.lowercase()
        } else if (value.startsWith("npub1") || value.startsWith("nprofile1")) {
            decodePublicKeyAsHexOrNull(value)
        } else {
            null
        }

    /**
     * `YYYY`, `YYYY-MM` or `YYYY-MM-DD` as the unix second that bound means, in the reader's own
     * timezone: `since` is the first instant of the span and `until` its last, so `until:2026`
     * includes all of December.
     *
     * The tokenizer already took the full ISO day, so what reaches here is the partial spellings
     * that name a span rather than a day.
     */
    fun parseDateToTimestamp(
        dateStr: String,
        field: DateField = DateField.SINCE,
    ): Long? {
        val parts = dateStr.split("-")
        if (parts.size > 3) return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        if (year < 1970 || year > 2100) return null
        val month = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else null
        if (month != null && (month < 1 || month > 12)) return null
        val day = if (parts.size > 2) parts[2].toIntOrNull() ?: return null else null

        return if (field == DateField.SINCE) {
            LocalClock.startOfDay(SearchDate.of(year, month ?: 1, day ?: 1) ?: return null)
        } else {
            val m = month ?: 12
            val d = day ?: SearchDate.lastDayOfMonth(year, m)
            LocalClock.endOfDay(SearchDate.of(year, m, d) ?: return null)
        }
    }

    private fun <T> MutableList<T>.addDistinct(value: T) {
        if (value !in this) add(value)
    }

    private class QueryBuilder {
        val authors = mutableListOf<String>()
        val authorNames = mutableListOf<String>()
        val mentions = mutableListOf<String>()
        val cites = mutableListOf<String>()
        val addrs = mutableListOf<String>()
        val kinds = mutableListOf<Int>()
        val hashtags = mutableListOf<String>()
        val labels = mutableListOf<String>()
        val groups = mutableListOf<String>()
        val scopes = mutableListOf<ExternalScope>()
        val excludeTerms = mutableListOf<String>()
        val pseudoKinds = mutableListOf<String>()
        val textParts = mutableListOf<String>()
        val orTerms = mutableListOf<String>()
        var since: Long? = null
        var until: Long? = null
        var language: String? = null
        var domain: String? = null

        /** Two of one date prefix keep the narrower bound, so a window can only ever shrink. */
        fun narrow(
            field: DateField,
            at: Long,
        ) {
            if (field == DateField.SINCE) {
                since = since?.let { maxOf(it, at) } ?: at
            } else {
                until = until?.let { minOf(it, at) } ?: at
            }
        }

        fun readOperator(token: Token.Operator) {
            when (token.name) {
                "from", "to" -> {
                    val hex = decodeQualifierPubkeyOrNull(token.value)
                    val into = if (token.name == "from") authors else mentions
                    if (hex != null) {
                        into.addDistinct(hex)
                    } else if (token.name == "from") {
                        // A NIP-05 or a display name; the picker resolves it to a key later.
                        authorNames.addDistinct(token.value)
                    } else {
                        textParts.add(token.raw)
                    }
                }

                "kind" -> {
                    if (KindRegistry.isPseudoKind(token.value)) {
                        pseudoKinds.addDistinct(token.value.lowercase())
                    } else {
                        val resolved = KindRegistry.resolve(token.value)
                        if (resolved != null) {
                            resolved.forEach { kinds.addDistinct(it) }
                        } else {
                            token.value.toIntOrNull()?.let { kinds.addDistinct(it) } ?: textParts.add(token.raw)
                        }
                    }
                }

                "since" ->
                    parseDateToTimestamp(token.value, DateField.SINCE)?.let { narrow(DateField.SINCE, it) }
                        ?: textParts.add(token.raw)

                "until" ->
                    parseDateToTimestamp(token.value, DateField.UNTIL)?.let { narrow(DateField.UNTIL, it) }
                        ?: textParts.add(token.raw)

                "lang" -> language = token.value.lowercase()
                "domain" -> domain = token.value.lowercase()
            }
        }

        fun build() =
            SearchQuery(
                text = SearchTokenizer.tidyTerms(textParts.joinToString(" ")),
                authors = authors.toImmutableList(),
                authorNames = authorNames.toImmutableList(),
                kinds = kinds.toImmutableList(),
                since = since,
                until = until,
                hashtags = hashtags.toImmutableList(),
                excludeTerms = excludeTerms.toImmutableList(),
                language = language,
                domain = domain,
                // More than three alternations is a query no relay ranks usefully.
                orTerms = orTerms.take(3).toPersistentList(),
                pseudoKinds = pseudoKinds.toImmutableList(),
                mentions = mentions.toImmutableList(),
                cites = cites.toImmutableList(),
                addrs = addrs.toImmutableList(),
                labels = labels.toImmutableList(),
                scopes = scopes.toImmutableList(),
                groups = groups.toImmutableList(),
            )
    }
}
