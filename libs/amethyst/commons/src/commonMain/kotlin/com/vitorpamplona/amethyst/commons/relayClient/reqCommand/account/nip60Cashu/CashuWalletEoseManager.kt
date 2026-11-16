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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip60Cashu

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletQueryState
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.cashuWalletFilters
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The account's NIP-60 wallet and NIP-61 nutzap inbox.
 *
 * This used to run from a collector inside `CashuWalletState`, on the account's own scope, which made
 * the wallet the only account-level subscription whose lifetime was decided by the model rather than
 * by a mount. It ran for every [com.vitorpamplona.amethyst.commons.model.Account] object that happened to be
 * resident — including accounts loaded purely so pushed gift wraps could be decrypted, which have no
 * wallet anyone is looking at — and the attempt to fix that bolted a "is this pubkey subscribed
 * anywhere" flow onto the model, so a model object was reading the relay layer's bookkeeping to
 * decide whether to talk to relays.
 *
 * As a manager in [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssembler]'s
 * group it starts and stops with every other account-level loader: the screen's mount for the account
 * on show, the registry for the rest, and whatever the foreground/background rule becomes without this
 * having to know about it. Exactly how NWC already worked.
 *
 * Per user, never merged: each account's wallet reads its own outbox for its own events and its own
 * inbox for nutzaps addressed to it, so there is no shared query to fold them into.
 */
class CashuWalletEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val account = key.account
        val wallet = account.cashuWalletState

        // NIP-65 outbox for our own wallet events; inbox + DM relays plus whatever our own kind:10019
        // advertises for inbound nutzaps, since another client may have published that with relays
        // unrelated to our NIP-65 lists.
        return cashuWalletFilters(
            CashuWalletQueryState(
                pubkey = account.userProfile().pubkeyHex,
                ownEventRelays = account.outboxRelays.flow.value,
                inboxRelays =
                    account.notificationRelays.flow.value +
                        account.dmRelays.flow.value +
                        (wallet.nutzapInfoEvent.value?.relays() ?: emptyList()),
            ),
            since,
        )
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }

        // The relay sets and the nutzap-info event all move the query, so each one re-invalidates.
        // Sampled because a relay-list edit can land as a burst of list events.
        userJobMap[user] =
            listOf(
                key.account.outboxRelays.flow,
                key.account.notificationRelays.flow,
                key.account.dmRelays.flow,
            ).map { flow ->
                key.account.scope.launch(Dispatchers.IO) {
                    flow.sample(1000).collectLatest { invalidateFilters() }
                }
            } +
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.cashuWalletState.nutzapInfoEvent
                        .sample(1000)
                        .collectLatest { invalidateFilters() }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
    }
}
