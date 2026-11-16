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
package com.vitorpamplona.amethyst.commons.model.serverList

import com.vitorpamplona.amethyst.commons.model.nip51Lists.indexerRelays.IndexerRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.searchRelays.SearchRelayListState
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The relays the app is **guessing** on the user's behalf because it has not seen their lists yet.
 *
 * Non-empty only while the corresponding event is absent — never because a list is empty, which is
 * a choice we honor (see `relayListOrDefaultsWhenUnknown`). It therefore empties itself, per list,
 * the moment the user's own data lands; no window, no timeout, no bookkeeping.
 *
 * **Deliberately NOT merged into [TrustedRelayListsState].** That one feeds `Account.isInMyRelayList`
 * -> `RelayAuthPermissionLedger` -> `RelayAuthResolver`, i.e. the NIP-42 AUTH decision. Guessed
 * relays must never make the app sign an AUTH challenge as though they were the user's own — that
 * would turn a timing signal into a signed identity assertion. The single consumer of this flow is
 * Tor routing.
 */
class AssumedRelayListsState(
    val nip65RelayList: Nip65RelayListState,
    val searchRelayList: SearchRelayListState,
    val indexerRelayList: IndexerRelayListState,
    val scope: CoroutineScope,
) {
    val flow: StateFlow<Set<NormalizedRelayUrl>> =
        combine(
            nip65RelayList.assumedDefaultsFlow,
            searchRelayList.assumedDefaultsFlow,
            indexerRelayList.assumedDefaultsFlow,
        ) { nip65, search, indexer ->
            nip65 + search + indexer
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                nip65RelayList.assumedDefaultsFlow.value +
                    searchRelayList.assumedDefaultsFlow.value +
                    indexerRelayList.assumedDefaultsFlow.value,
            )
}
