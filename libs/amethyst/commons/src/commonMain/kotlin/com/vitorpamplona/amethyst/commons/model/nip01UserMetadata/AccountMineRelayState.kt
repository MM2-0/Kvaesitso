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
package com.vitorpamplona.amethyst.commons.model.nip01UserMetadata

import com.vitorpamplona.amethyst.commons.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.commons.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.commons.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The set of relays to read the user's *own* events from for the "Mine" top-nav selection:
 * the user's NIP-65 outbox, their private-storage relays, their local relays and their proxy
 * relays. Deliberately mirrors [AccountOutboxRelayState] **minus broadcast**: broadcast relays are
 * write-only blast targets, so reading the user's own content back from them is wrong — they don't
 * serve reads and would only waste a subscription.
 */
class AccountMineRelayState(
    nip65: Nip65RelayListState,
    privateStorage: PrivateStorageRelayListState,
    local: LocalRelayListState,
    proxy: ProxyRelayListState,
    scope: CoroutineScope,
) {
    val flow =
        combine(
            nip65.outboxFlow,
            privateStorage.flow,
            local.flow,
            proxy.flow,
        ) { nip65Outbox, privateOutBox, localRelays, proxyRelays ->
            nip65Outbox + privateOutBox + localRelays + proxyRelays
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                nip65.outboxFlow.value +
                    privateStorage.flow.value +
                    local.flow.value +
                    proxy.flow.value,
            )
}
