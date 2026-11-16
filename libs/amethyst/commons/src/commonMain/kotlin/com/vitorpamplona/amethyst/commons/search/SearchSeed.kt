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

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The query a screen hands the search box when its search button is tapped.
 *
 * A screen is already a filter — the articles feed is `kind:30023`, a profile is one author, a
 * location channel is one geohash — and until now none of that survived the tap: search opened
 * empty and the reader retyped what the screen already knew. These builders say that filter in
 * the field's own token language, so it arrives as chips the reader can narrow, widen or drop.
 *
 * Every seed is a *starting point*, never a constraint: it becomes ordinary text in the box.
 */
object SearchSeed {
    /** The kind window a feed shows, as a seed. */
    fun ofKinds(vararg kinds: Int) = SearchQuery(kinds = kinds.toList().toImmutableList())

    /** Everything one person wrote — the profile screen's seed. */
    fun byAuthor(pubkeyHex: HexKey) = SearchQuery(authors = persistentListOf(pubkeyHex))

    /** One topic — the hashtag feed's seed. */
    fun byHashtag(tag: String) = SearchQuery(hashtags = persistentListOf(tag.removePrefix("#")))

    /** One place, as the NIP-73 `geo:` scope — the location channels' seed. */
    fun byGeohash(geohash: String) = SearchQuery(scopes = persistentListOf(ExternalScope("geo", geohash)))

    /** One NIP-29 room — the relay-group screens' seed. */
    fun byGroup(groupId: String) = SearchQuery(groups = persistentListOf(groupId))

    /**
     * Two seeds as one query. Used to fold a feed's kind window together with whatever its
     * top-nav filter narrowed it to, so the articles feed showing `#nostr` seeds both.
     */
    fun merge(
        a: SearchQuery,
        b: SearchQuery,
    ) = SearchQuery(
        text = listOf(a.text, b.text).filter { it.isNotBlank() }.joinToString(" "),
        authors = (a.authors + b.authors).distinct().toImmutableList(),
        authorNames = (a.authorNames + b.authorNames).distinct().toImmutableList(),
        kinds = (a.kinds + b.kinds).distinct().toImmutableList(),
        since = a.since ?: b.since,
        until = a.until ?: b.until,
        hashtags = (a.hashtags + b.hashtags).distinct().toImmutableList(),
        excludeTerms = (a.excludeTerms + b.excludeTerms).distinct().toImmutableList(),
        language = a.language ?: b.language,
        domain = a.domain ?: b.domain,
        orTerms = (a.orTerms + b.orTerms).distinct().toImmutableList(),
        pseudoKinds = (a.pseudoKinds + b.pseudoKinds).distinct().toImmutableList(),
        mentions = (a.mentions + b.mentions).distinct().toImmutableList(),
        cites = (a.cites + b.cites).distinct().toImmutableList(),
        addrs = (a.addrs + b.addrs).distinct().toImmutableList(),
        labels = (a.labels + b.labels).distinct().toImmutableList(),
        scopes = (a.scopes + b.scopes).distinct().toImmutableList(),
        groups = (a.groups + b.groups).distinct().toImmutableList(),
    )
}

/**
 * The part of a feed's top-nav filter the search language can actually say.
 *
 * A hashtag, a geohash, a community and "Mine" each have a token that asks a relay for exactly
 * that set, so they seed. A follow set does not: `AllFollows` and a `PeopleList` are hundreds of
 * keys, and spelling them out would fill the box with `from:` chips nobody can read past — and
 * dropping the ones that did not fit would quietly seed a *different*, narrower query than the
 * feed the reader was looking at. So those return [SearchQuery.EMPTY] and the seed keeps only
 * the feed's kind window, which is true as far as it goes.
 *
 * [mePubkeyHex] is the reader's own key, which is the only thing "Mine" can mean.
 */
fun TopFilter.asSearchQuery(mePubkeyHex: HexKey?): SearchQuery =
    when (this) {
        is TopFilter.Hashtag -> SearchSeed.byHashtag(tag)
        is TopFilter.Geohash -> SearchSeed.byGeohash(tag)
        is TopFilter.Mine -> mePubkeyHex?.let { SearchSeed.byAuthor(it) } ?: SearchQuery.EMPTY
        // A community's own address, asked with `#a` — the tag its posts carry.
        is TopFilter.Community -> SearchQuery(addrs = persistentListOf(address.toValue()))
        else -> SearchQuery.EMPTY
    }
