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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.favoriteAlgoFeeds

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Top-nav filter backed by the latest kind-6300 response from a favorite algo feed.
 *
 * The filter is a pure immutable membership check: [match] accepts a note only if the
 * DVM's latest response included it. When a new response arrives, a new instance is
 * emitted through [FavoriteAlgoFeedFlow] and replaces the active filter.
 */
@Immutable
class FavoriteAlgoFeedTopNavFilter(
    val feedAddress: Address,
    val acceptedIds: Set<HexKey>,
    val acceptedAddresses: Set<String>,
    val contentRelays: Set<NormalizedRelayUrl>,
    val listenRelays: Set<NormalizedRelayUrl>,
    val requestId: HexKey?,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = true

    override fun match(noteEvent: Event): Boolean =
        noteEvent.id in acceptedIds ||
            (noteEvent is AddressableEvent && noteEvent.addressTag() in acceptedAddresses)

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<FavoriteAlgoFeedTopNavPerRelayFilterSet> = MutableStateFlow(startValue(cache))

    override fun startValue(cache: ICacheProvider): FavoriteAlgoFeedTopNavPerRelayFilterSet =
        FavoriteAlgoFeedTopNavPerRelayFilterSet(
            contentFetches =
                contentRelays.associateWith {
                    FavoriteAlgoFeedTopNavPerRelayFilter(
                        ids = acceptedIds,
                        addresses = acceptedAddresses,
                    )
                },
            listenRelays = listenRelays,
            requestIds = setOfNotNull(requestId),
        )
}
