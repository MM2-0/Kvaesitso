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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

interface IFeedTopNavFilter {
    fun matchAuthor(pubkey: HexKey): Boolean

    fun match(noteEvent: Event): Boolean

    fun toPerRelayFlow(cache: ICacheProvider): Flow<IFeedTopNavPerRelayFilterSet>

    fun startValue(cache: ICacheProvider): IFeedTopNavPerRelayFilterSet

    /**
     * The members of a selected list that this feed reads from their own outbox relays (their
     * kind:10002). They are collected across every screen into `Account.selectedListAuthors` and
     * watched through the user finder, so their relay lists load even when nobody follows them.
     *
     * The follows feeds return nothing: `AccountFollowsLoaderSubAssembler` already loads relay lists
     * for their whole author set (kind:3 plus the user's own lists and follow packs) at startup.
     */
    fun outboxAuthors(): Set<HexKey> = emptySet()
}

/** The union of [IFeedTopNavFilter.outboxAuthors] across several feeds' selections. */
fun mergeOutboxAuthors(filters: List<IFeedTopNavFilter>): Set<HexKey> {
    val merged = mutableSetOf<HexKey>()
    filters.forEach { merged.addAll(it.outboxAuthors()) }
    return merged
}
