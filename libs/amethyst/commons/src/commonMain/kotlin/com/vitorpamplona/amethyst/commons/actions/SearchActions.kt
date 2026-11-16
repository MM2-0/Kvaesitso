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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent

/**
 * Pure NIP-50 search-filter assembly + search-relay resolution.
 *
 * Builds [Filter]s and picks relays — caller drives subscription / drain.
 * Non-UI callers should layer the following on top to match Amethyst's
 * in-app search behavior:
 *
 *  * **Drain / subscribe.** A function-call API (amy `search`, Gemini App
 *    Functions) usually wants `client.subscribe(...)` until every relay
 *    sends EOSE or a short timeout elapses, then unsubscribe. The Amethyst
 *    foreground UI uses a live subscription instead — it stays open as the
 *    user types.
 *  * **Dedup.** Profile search dedups by `pubKey` (multiple kind:0 events
 *    per author); note search dedups by event id. Both pick the freshest
 *    revision via `sortedByDescending { createdAt }.distinctBy { … }`.
 *  * **Pseudo-kind filtering.** When you let callers ask for `reply` /
 *    `media` / exclusion terms, apply
 *    [com.vitorpamplona.amethyst.commons.search.SearchResultFilter] after
 *    the drain. This filter is NOT exposed in the filter API itself.
 *  * **Debounce.** Interactive callers should debounce input. The
 *    Amethyst UI uses 300 ms before issuing a new subscription; one-shot
 *    callers (amy, App Functions) skip this.
 */
object SearchActions {
    /** Default kinds for "search notes" — kind:1 short text notes. */
    val DEFAULT_NOTE_KINDS: List<Int> = listOf(TextNoteEvent.KIND)

    /**
     * Build a NIP-50 filter for searching kind:0 profile metadata.
     *
     * Returns null for a blank [query] — callers should treat as "no
     * results" rather than issuing an unconstrained search that most
     * relays would reject anyway.
     */
    fun searchProfilesFilter(
        query: String,
        limit: Int = 20,
    ): Filter? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return Filter(
            kinds = listOf(MetadataEvent.KIND),
            search = q,
            limit = limit,
        )
    }

    /**
     * Build a NIP-50 filter for searching event content. Defaults to
     * kind:1 short text notes; pass [kinds] to widen (e.g. include
     * kind:30023 long-form or kind:9802 highlights).
     */
    fun searchNotesFilter(
        query: String,
        kinds: List<Int> = DEFAULT_NOTE_KINDS,
        limit: Int = 50,
        since: Long? = null,
        until: Long? = null,
    ): Filter? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return Filter(
            kinds = kinds,
            search = q,
            limit = limit,
            since = since,
            until = until,
        )
    }

    /**
     * Pick the relay set to query for NIP-50 search.
     *
     * Strategy: when [currentList] (the user's kind:10007 search-relay
     * list) is present, use its public + decrypted-private relays.
     * Otherwise fall back to [fallback] (defaults to Amethyst's curated
     * [DefaultSearchRelayList] — the same set the Android UI uses when
     * the user hasn't configured their own).
     *
     * [signer] is only consulted when [currentList] is non-null and has
     * private (NIP-44 encrypted) relay entries; an internal/local signer
     * is fine, a NIP-46/NIP-55 signer will cost a round-trip.
     */
    suspend fun resolveSearchRelays(
        signer: NostrSigner,
        currentList: SearchRelayListEvent?,
        fallback: Collection<NormalizedRelayUrl> = DefaultSearchRelayList,
    ): Set<NormalizedRelayUrl> {
        if (currentList == null) return fallback.toSet()
        val combined = currentList.relays(signer)
        return if (combined.isEmpty()) fallback.toSet() else combined.toSet()
    }
}
