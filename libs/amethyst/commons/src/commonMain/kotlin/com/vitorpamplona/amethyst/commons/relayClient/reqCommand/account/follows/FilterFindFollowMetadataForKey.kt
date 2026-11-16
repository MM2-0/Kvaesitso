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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.follows

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.user.pickRelaysToLoadUsers
import com.vitorpamplona.amethyst.commons.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

fun pickRelaysToLoadUsers(
    users: Set<User>,
    accounts: Collection<Account>,
    connected: Set<NormalizedRelayUrl>,
    cannotConnectRelays: Set<NormalizedRelayUrl>,
    hasTried: EOSEAccountFast<User>,
): Map<NormalizedRelayUrl, Set<HexKey>> {
    val indexRelays = mutableSetOf<NormalizedRelayUrl>()
    val homeRelays = mutableSetOf<NormalizedRelayUrl>()
    val searchRelays = mutableSetOf<NormalizedRelayUrl>()
    val commonRelays = mutableSetOf<NormalizedRelayUrl>()

    accounts.forEach { key ->
        indexRelays.addAll(
            key.indexerRelayList.flow.value
                .ifEmpty { DefaultIndexerRelayList },
        )

        homeRelays.addAll(key.nip65RelayList.allFlowNoDefaults.value)
        homeRelays.addAll(key.privateStorageRelayList.flow.value)
        homeRelays.addAll(key.localRelayList.flow.value)

        searchRelays.addAll(key.trustedRelayList.flow.value)
        searchRelays.addAll(
            key.searchRelayList.flow.value
                .ifEmpty { DefaultSearchRelayList },
        )

        // uses followShared to ignore personal relays when finding users.
        commonRelays.addAll(
            key.followSharedOutboxesOrProxy.flow.value
                .ifEmpty { Constants.eventFinderRelays },
        )
    }

    return pickRelaysToLoadUsers(
        users,
        LocalCache.relayHints,
        indexRelays - cannotConnectRelays,
        homeRelays - cannotConnectRelays,
        searchRelays - cannotConnectRelays,
        connected,
        commonRelays - cannotConnectRelays,
        cannotConnectRelays,
        hasTried,
    )
}
