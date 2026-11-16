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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent

/**
 * A [SearchQuery] as the REQ a search sends: NIP-01 filters, ORed inside one subscription.
 *
 * Every token the field draws as a chip becomes a *filter field* here, not a NIP-50 search
 * extension — `#p`, `#e`, `#a`, `#t`, `#l`, `#h`, `#i`/`#I`, `since`/`until` — so a relay applies
 * them as index lookups and is left to rank only the free text. That is what lets a chip narrow
 * a search without fighting the relay's own relevance ordering.
 *
 * Most of the shape here is one question: which tag does this token actually get asked with?
 * A hashtag is three (`#t` on the event, `#l` on a label, `#i`/`#I` on a comment written about
 * it), and those cannot go in one filter — a single filter ANDs its tag fields, which would ask
 * for events carrying all three at once. So they fan out into a union instead, and the secondary
 * arms take a smaller limit because a relay applies `limit` per filter.
 */
object SearchFilterBuilder {
    /** How many results a secondary arm of a union may return, given the page's own limit. */
    private fun sideLimit(limit: Int) = maxOf(4, (limit / 4.0).toInt())

    // A NIP-22 comment names its thread's root scope in `I` and its parent's in `i`. One filter
    // per tag: both in one filter would AND them and drop the deeper replies.
    private val COMMENT_SCOPE_TAGS = listOf("I", "i")

    /**
     * The filters this query sends. [kinds] is the caller’s kind window (null asks every kind),
     * [limit] the page size, and [searchString] the caller’s own NIP-50 builder over the leftover
     * terms — that is where `language:`, `domain:` and any relay-specific extension belong.
     *
     * A `from:` name the parser could not decode stays a name here: it is not a filter field,
     * and the field’s in-place rewrite ([AuthorNameRewrite]) turns it into the `from:npub1…`
     * that lands in [SearchQuery.authors] once the name resolves.
     */
    fun build(
        query: SearchQuery,
        kinds: List<Int>? = null,
        limit: Int = 100,
        searchString: (SearchQuery) -> String? = ::defaultSearchString,
    ): List<Filter> {
        if (query.isEmpty) return emptyList()

        val tags = mutableMapOf<String, List<String>>()
        if (query.mentions.isNotEmpty()) tags["p"] = query.mentions.toList()
        if (query.cites.isNotEmpty()) tags["e"] = query.cites.toList()
        if (query.addrs.isNotEmpty()) tags["a"] = query.addrs.toList()

        val search = searchString(query)?.takeIf { it.isNotBlank() }
        val authors = query.authors.takeIf { it.isNotEmpty() }?.toList()

        // `from:vitor` names somebody the picker has not resolved to a key yet, and an unresolved
        // name is not a filter field. Left alone it would produce a filter constrained by nothing
        // but its kinds — an unbounded REQ to every relay, and locally the first N notes of the
        // cache dressed up as results. A query that can express nothing asks nothing.
        if (search == null && authors == null && tags.isEmpty() &&
            query.hashtags.isEmpty() && query.labels.isEmpty() && query.scopes.isEmpty() && query.groups.isEmpty() &&
            query.since == null && query.until == null
        ) {
            return emptyList()
        }

        // Built per arm rather than shared, so the window and the mention tags ride every filter
        // of a union while each arm still adds the one tag that arm is about.
        fun arm(
            armKinds: List<Int>?,
            armTag: Pair<String, List<String>>? = null,
            armLimit: Int = limit,
        ) = Filter(
            kinds = armKinds,
            authors = authors,
            tags = (tags + listOfNotNull(armTag)).takeIf { it.isNotEmpty() },
            since = query.since,
            until = query.until,
            limit = armLimit,
            search = search,
        )

        val scoped = query.scopes.flatMap { it.ids() }.distinct()
        val tagged = query.hashtags.flatMap { ScopeIds.tagValues(it) }.distinct()
        val labelled = query.labels.flatMap { ScopeIds.tagValues(it) }.distinct()

        if (tagged.isEmpty() && scoped.isEmpty() && query.groups.isEmpty() && labelled.isEmpty()) {
            return listOf(arm(kinds))
        }

        val side = sideLimit(limit)
        val filters = mutableListOf<Filter>()

        // A `label:` asks for the labels themselves, so its filter names kind 1985 over the
        // caller's: the mark lives on the label event, not on what the label names.
        if (labelled.isNotEmpty()) {
            filters.add(arm(listOf(LabelEvent.KIND), "l" to labelled))
        }

        if (query.groups.isNotEmpty()) {
            filters.add(arm(kinds, "h" to query.groups.toList()))
            // Plus the group's own metadata, so a result set can name the room it came from.
            // Deliberately bare: a kind-39000 event is written by the host relay, not by the
            // author being searched for, and carries the room's name rather than the query's
            // terms — inheriting either would make it match nothing exactly when it is needed.
            filters.add(
                Filter(
                    kinds = listOf(GroupMetadataEvent.KIND),
                    tags = mapOf("d" to query.groups.toList()),
                    limit = side,
                ),
            )
        }

        if (tagged.isNotEmpty()) {
            filters.add(arm(kinds, "t" to tagged))
            filters.add(arm(kinds, "l" to tagged, side))
            // Comments written *about* the topic, which carry it as a NIP-73 external id.
            if (kinds == null || CommentEvent.KIND in kinds) {
                val ids = ScopeIds.hashtagIds(query.hashtags)
                COMMENT_SCOPE_TAGS.forEach { tag ->
                    filters.add(arm(listOf(CommentEvent.KIND), tag to ids, side))
                }
            }
        }

        if (scoped.isNotEmpty()) {
            // A scope is only ever a comment's question, so it names its kind over the caller's.
            filters.add(arm(listOf(CommentEvent.KIND), "I" to scoped))
            filters.add(arm(listOf(CommentEvent.KIND), "i" to scoped, side))
        }

        return filters
    }

    /**
     * The default NIP-50 string: the leftover terms, plus the extensions that have no NIP-01
     * equivalent and so have to be asked of the relay's own search.
     */
    fun defaultSearchString(query: SearchQuery): String? {
        val parts = mutableListOf<String>()
        if (query.orTerms.isNotEmpty()) {
            parts.add(query.orTerms.joinToString(" OR "))
        }
        if (query.text.isNotBlank()) parts.add(query.text)
        query.language?.let { parts.add("language:$it") }
        query.domain?.let { parts.add("domain:$it") }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }
}
