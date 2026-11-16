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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * A [SearchQuery] written back as the text the field holds.
 *
 * The round trip matters: this is what a saved search, a shared url and the form panel all put
 * back into the box, and [QueryParser] has to read it as the same query. So every token is
 * written in the one spelling the tokenizer takes — an npub rather than hex, an ISO day rather
 * than a relative one — and anything that has no unambiguous spelling is left out rather than
 * written in a form that would come back meaning something else.
 */
object QuerySerializer {
    fun serialize(query: SearchQuery): String {
        if (query.isEmpty) return ""

        val parts = mutableListOf<String>()

        query.authors.forEach { hex -> parts.add("from:${npubOrNull(hex) ?: hex}") }
        query.authorNames.forEach { name -> parts.add("from:$name") }
        query.mentions.forEach { hex -> parts.add("to:${npubOrNull(hex) ?: hex}") }
        query.cites.forEach { id -> noteOrNull(id)?.let { parts.add("to:$it") } }
        query.addrs.forEach { aTag -> naddrOrNull(aTag)?.let { parts.add("to:$it") } }

        KindRegistry.tokenize(query.kinds).forEach { kind -> parts.add("kind:$kind") }
        query.pseudoKinds.forEach { pseudo -> parts.add("kind:$pseudo") }

        // The local day the bound falls on, which is the day the reader picked in the calendar.
        query.since?.let { parts.add("since:${dayOf(it, DateField.SINCE)}") }
        query.until?.let { parts.add("until:${dayOf(it, DateField.UNTIL)}") }

        query.language?.let { parts.add("lang:$it") }
        query.domain?.let { parts.add("domain:$it") }

        query.groups.forEach { id -> if (tokenizes(id)) parts.add("group:$id") }
        query.labels.forEach { mark -> if (tokenizes(mark)) parts.add("label:$mark") }
        query.scopes.forEach { scope -> if (tokenizes(scope.value)) parts.add(scope.token()) }
        query.hashtags.forEach { tag -> parts.add("#$tag") }

        if (query.text.isNotBlank()) parts.add(query.text)
        if (query.orTerms.isNotEmpty()) parts.add(query.orTerms.joinToString(" OR "))
        query.excludeTerms.forEach { term -> parts.add("-$term") }

        return parts.joinToString(" ")
    }

    /**
     * Can this value be written as a token that reads back as itself? Whitespace or trailing
     * sentence punctuation in a group id would silently point at somebody else's room, and in a
     * NIP-32 mark it would silently ask for a different mark — so the token is refused instead.
     */
    fun tokenizes(value: String?): Boolean {
        val v = value ?: return false
        if (v.isEmpty()) return false
        if (v.any { it.isWhitespace() }) return false
        return v.last() !in ".,;!?"
    }

    /** The `YYYY-MM-DD` a bound falls on, in the reader's timezone. */
    fun dayOf(
        timestamp: Long,
        field: DateField,
    ): String = DateUtils.localDay(timestamp, field).ymd()

    fun timestampToDate(timestamp: Long): String = DateUtils.timestampToDate(timestamp)

    private fun npubOrNull(hex: String) =
        try {
            NPub.create(hex)
        } catch (_: Exception) {
            null
        }

    private fun noteOrNull(hex: String) =
        try {
            NNote.create(hex)
        } catch (_: Exception) {
            null
        }

    private fun naddrOrNull(aTag: String) =
        try {
            Address.parse(aTag)?.let { NAddress.createOrNull(it.kind, it.pubKeyHex, it.dTag, emptyList()) }
        } catch (_: Exception) {
            null
        }
}
