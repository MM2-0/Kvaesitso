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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds.global

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

@Immutable
class GlobalTopNavFilter(
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val proxyRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val relayFeeds: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = true

    override fun match(noteEvent: Event) = true

    override fun toPerRelayFlow(cache: ICacheProvider): Flow<GlobalTopNavPerRelayFilterSet> =
        combine(outboxRelays, proxyRelays, relayFeeds) { outboxRelays, proxyRelays, relayFeeds ->
            if (proxyRelays.isNotEmpty()) {
                GlobalTopNavPerRelayFilterSet(proxyRelays.associateWith { GlobalTopNavPerRelayFilter })
            } else {
                GlobalTopNavPerRelayFilterSet((relayFeeds + outboxRelays).associateWith { GlobalTopNavPerRelayFilter })
            }
        }

    override fun startValue(cache: ICacheProvider): GlobalTopNavPerRelayFilterSet =
        if (proxyRelays.value.isNotEmpty()) {
            GlobalTopNavPerRelayFilterSet(proxyRelays.value.associateWith { GlobalTopNavPerRelayFilter })
        } else {
            GlobalTopNavPerRelayFilterSet((relayFeeds.value + outboxRelays.value).associateWith { GlobalTopNavPerRelayFilter })
        }
}
