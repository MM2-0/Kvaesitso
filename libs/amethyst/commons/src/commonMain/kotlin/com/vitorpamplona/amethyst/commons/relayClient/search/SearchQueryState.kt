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
package com.vitorpamplona.amethyst.commons.relayClient.search

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.MutableQueryState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A live search box. The [searchQuery] flow is the subscription id: every edit re-issues the
 * REQs, so the sub-assemblers read the relay lists as snapshots at filter-build time.
 *
 * @param searchRelays NIP-50 search relays (kind 10007), for full-text people/post search.
 * @param indexerRelays indexer relays, tried first when the query is a bare pubkey / npub / nprofile.
 * @param followPlusAllMineWithSearchRelays follows' outboxes + every relay of mine + the search
 *   relays; the fallback set for a direct id lookup that no hint resolves.
 */
@Stable
class SearchQueryState(
    val searchQuery: MutableStateFlow<String>,
    override val account: IAccount,
    val searchRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val indexerRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val followPlusAllMineWithSearchRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : MutableQueryState,
    AccountScopedQuery {
    // The text alone re-issues the REQs: a `from:` name the field could not decode is rewritten
    // in place to `from:npub1…` once it resolves (see AuthorNameRewrite), which is a text change
    // like any other and arrives through this same flow.
    override fun flow(): Flow<*> = searchQuery

    /**
     * Which relays this query went to, and which have answered.
     *
     * The screen otherwise has no idea: it guesses a search is done by waiting a fixed moment
     * after the last keystroke, because nothing told it otherwise. The sub-assemblers know both
     * halves already -- they build the per-relay filters, and they are handed every EOSE -- so
     * they record it here rather than keeping it to themselves.
     *
     * "Waiting on" is [asked] minus [answered]. A relay that never sends EOSE simply stays in it,
     * which is the honest answer and the reason the timer stays as a backstop rather than being
     * replaced by this.
     */
    val asked = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val answered = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /** The query the two sets describe, so a new one starts from nothing. */
    private var askedFor: String? = null

    /**
     * Records the relays one sub-assembler is about to ask for [query].
     *
     * Additive, and reset by the query changing rather than by being called: the posts and the
     * people assemblers both rebuild off this same state, so a call that replaced the set would
     * leave whichever ran second looking like the only one that asked anything.
     */
    fun startedAsking(
        query: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (askedFor != query) {
            askedFor = query
            asked.value = relays
            answered.value = emptySet()
        } else {
            asked.value = asked.value + relays
        }
    }

    fun answeredBy(relay: NormalizedRelayUrl) {
        answered.value = answered.value + relay
    }
}
