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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * A from:<name> / to:<name> token in the typed text that names a person by *name* — a NIP-05
 * address, a .bit name, a d/ or id/ identifier — rather than by a key the parser could decode
 * on the spot. [start] to [end] is the half-open span of the whole token, qualifier included,
 * so a resolution can be spliced over it without re-reading the field.
 */
data class UnresolvedAuthorName(
    val field: KeyField,
    val name: String,
    val start: Int,
    val end: Int,
)

/**
 * The in-place rewrite of the author qualifiers: a from:<name> / to:<name> the parser could
 * not decode becomes, once a resolver has named its key, exactly the text a people-picker pick
 * writes — from:npub1… / to:npub1…. The text stays the single source of truth: the parser,
 * the chip rendering, the cache filter and the relay REQ all read the rewritten token with no
 * side channel, and the rewrite never loops because a token holding a key is finished.
 *
 * The scan is the parser’s own second pass with positions, so the two can never disagree about
 * what counts as a token: a qualifier inside a "quoted phrase" or under a -negation is literal
 * text and is never rewritten, and a to: that has begun a NIP-19 pointer (to:note1…) names an
 * event, not a person, and is left alone.
 */
object AuthorNameRewrite {
    /** A to: value with one of these beginnings is a NIP-19 pointer, not a person’s name. */
    private val POINTER_PREFIXES = listOf("note1", "nevent1", "naddr1")

    /**
     * The author-qualifier tokens of [text] still holding a name, in the order typed. A token
     * whose value already decodes as a key is finished — there is nothing to resolve, and
     * rewriting it again would loop.
     */
    fun unresolvedAuthorNames(text: String): List<UnresolvedAuthorName> =
        QueryParser.positionedTokens(text).mapNotNull { positioned ->
            val token = positioned.token as? Token.Operator ?: return@mapNotNull null
            val field = KeyField.of(token.name) ?: return@mapNotNull null
            if (QueryParser.decodeQualifierPubkeyOrNull(token.value) != null) return@mapNotNull null
            if (POINTER_PREFIXES.any { token.value.startsWith(it, ignoreCase = true) }) return@mapNotNull null
            UnresolvedAuthorName(field, token.value, positioned.start, positioned.end)
        }

    /**
     * [text] with [token] rewritten to <field>:npub1… — the text a pick writes — or null when
     * [pubkeyHex] is not a key, which is never written: a name must stay a name rather than be
     * replaced by garbage.
     *
     * Only the token’s own span is touched, so when several tokens of one text are rewritten the
     * splices must run right to left: a rewritten token is a different length than the name it
     * replaced, and the spans still to come were computed on the original text.
     */
    fun rewrite(
        text: String,
        token: UnresolvedAuthorName,
        pubkeyHex: HexKey,
    ): String? {
        val npub =
            try {
                NPub.create(pubkeyHex)
            } catch (_: Exception) {
                return null
            }
        return text.substring(0, token.start) + "${token.field.token}:$npub" + text.substring(token.end)
    }
}
