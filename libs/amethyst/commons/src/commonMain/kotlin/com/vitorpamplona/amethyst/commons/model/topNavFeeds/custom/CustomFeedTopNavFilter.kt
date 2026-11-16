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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.custom

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHashes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * A feed the user built: every condition that is set must hold (the kinds, the authors, at least one
 * of the hashtags), and nothing from an excluded author or containing an excluded keyword passes.
 * The relays come from, in order: the feed's own relays, the proxy relays, the authors' outbox
 * relays, or the account's follows relays (for a hashtag-only feed). A feed whose definition is gone
 * ([source] null) shows nothing.
 */
@Immutable
class CustomFeedTopNavFilter(
    val source: FeedSource.Filter?,
    val followsRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: Set<NormalizedRelayUrl>,
) : IFeedTopNavFilter {
    val authors: Set<HexKey> = source?.authors?.toSet().orEmpty()
    val hashtags: Set<String> = source?.hashtags?.mapTo(mutableSetOf()) { it.lowercase() }.orEmpty()
    val kinds: Set<Int> = source?.kinds?.toSet().orEmpty()
    val excludeAuthors: Set<HexKey> = source?.excludeAuthors?.toSet().orEmpty()
    val excludeKeywords: List<String> = source?.excludeKeywords?.filter { it.isNotBlank() }.orEmpty()
    val ownRelays: Set<NormalizedRelayUrl> = source?.relays?.mapNotNullTo(mutableSetOf()) { it.normalizeRelayUrlOrNull() }.orEmpty()

    override fun matchAuthor(pubkey: HexKey): Boolean = source != null && pubkey !in excludeAuthors && (authors.isEmpty() || pubkey in authors)

    override fun match(noteEvent: Event): Boolean {
        if (source == null) return false
        if (kinds.isNotEmpty() && noteEvent.kind !in kinds) return false
        if (!matchAuthor(noteEvent.pubKey)) return false
        if (hashtags.isNotEmpty() && !noteEvent.isTaggedHashes(hashtags)) return false
        return excludeKeywords.none { noteEvent.content.contains(it, ignoreCase = true) }
    }

    private fun scope(authors: Set<HexKey>) = CustomFeedTopNavPerRelayFilter(authors, hashtags, kinds)

    private fun everywhere(relays: Set<NormalizedRelayUrl>) = CustomFeedTopNavPerRelayFilterSet(relays.associateWith { scope(authors) })

    private fun byOutbox(authorsPerRelay: Map<NormalizedRelayUrl, Set<HexKey>>) = CustomFeedTopNavPerRelayFilterSet(authorsPerRelay.mapValues { scope(it.value) })

    // The authors whose own relays this feed reads: only when it names authors and pins no relays of
    // its own or a proxy. Routes both the REQ (toPerRelayFlow) and the relay-list loading (outboxAuthors).
    private val authorsByOutbox: Set<HexKey>? =
        if (source != null && ownRelays.isEmpty() && proxyRelays.isEmpty() && authors.isNotEmpty()) authors else null

    override fun outboxAuthors(): Set<HexKey> = authorsByOutbox.orEmpty()

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<CustomFeedTopNavPerRelayFilterSet> =
        when {
            source == null -> MutableStateFlow(CustomFeedTopNavPerRelayFilterSet(emptyMap()))
            authorsByOutbox != null ->
                combine(OutboxRelayLoader().toAuthorsPerRelayFlow(authorsByOutbox, cache) { it }, blockedRelays) { perRelay, blocked ->
                    byOutbox(perRelay.minus(blocked))
                }
            ownRelays.isNotEmpty() -> MutableStateFlow(everywhere(ownRelays))
            proxyRelays.isNotEmpty() -> MutableStateFlow(everywhere(proxyRelays))
            else -> followsRelays.map { everywhere(it) }
        }

    override fun startValue(cache: ICacheProvider): CustomFeedTopNavPerRelayFilterSet =
        when {
            source == null -> CustomFeedTopNavPerRelayFilterSet(emptyMap())
            authorsByOutbox != null -> byOutbox(OutboxRelayLoader().authorsPerRelaySnapshot(authorsByOutbox, cache) { it }.minus(blockedRelays.value))
            ownRelays.isNotEmpty() -> everywhere(ownRelays)
            proxyRelays.isNotEmpty() -> everywhere(proxyRelays)
            else -> everywhere(followsRelays.value)
        }
}

/** What one relay is asked for: [authors] (empty for any author), [hashtags] and [kinds] (empty for the defaults). */
@Immutable
class CustomFeedTopNavPerRelayFilter(
    val authors: Set<HexKey>,
    val hashtags: Set<String>,
    val kinds: Set<Int>,
) : IFeedTopNavPerRelayFilter

class CustomFeedTopNavPerRelayFilterSet(
    val set: Map<NormalizedRelayUrl, CustomFeedTopNavPerRelayFilter>,
) : IFeedTopNavPerRelayFilterSet {
    override fun scopeFor(relay: NormalizedRelayUrl) = set[relay]

    override fun relays() = set.keys
}
