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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip47WalletConnect

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Always-on subscription for NIP-47 wallet notifications (kind 23197/23196),
 * grouped in [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssembler]
 * so it shares the exact lifecycle of the account's zap/notification inbox
 * subscription: open while logged in, kept warm in the background by
 * `NotificationRelayService`, torn down on logout.
 *
 * For each configured NWC wallet it queries the wallet's own relay (not the inbox
 * relays) for notifications `p`-tagged to that wallet's client pubkey. Unlike the
 * inbox managers — whose events land in `LocalCache` — these are ephemeral and
 * encrypted per wallet, so decryption + fan-out happens in [onEvent] via
 * `NwcSignerState.handleIncomingNotification`, which publishes non-zap payments to
 * `incomingNonZapPayments`. Since `since` is floored at watch start, relaunching
 * never replays old payments; the `seen` set de-dupes re-delivery on reconnect.
 */
class NwcNotificationsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    private val startSince = TimeUtils.now()

    // Only needs to cover re-delivery on reconnect; an unbounded set kept every payment id ever seen.
    private val seen = LruCache<HexKey, Boolean>(500)
    private val userJobMap = mutableMapOf<User, List<Job>>()

    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val account = key.account

        return account.settings.nwcWallets.value.flatMap { wallet ->
            val signer = account.nip47SignerState.buildSigner(wallet.uri) ?: return@flatMap emptyList()

            // Skip wallets that advertise no notification support; warm the cache
            // (and re-evaluate on the next invalidation) when the info is unknown.
            account.nwcInfoCache.refreshIfStale(wallet.uri)
            if (account.nwcInfoCache.current(wallet.uri)?.supportsNotifications() == false) {
                return@flatMap emptyList()
            }

            listOf(
                RelayBasedFilter(
                    relay = wallet.uri.relayUri,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.NWC,
                            kinds = listOf(NwcNotificationEvent.KIND, NwcNotificationEvent.LEGACY_KIND),
                            authors = listOf(wallet.uri.pubKeyHex),
                            tags = mapOf("p" to listOf(signer.pubKey)),
                            since = since?.get(wallet.uri.relayUri)?.time ?: startSince,
                        ),
                ),
            )
        }
    }

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                // Re-subscribe when the wallet set changes so relays/keys are added or dropped.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.settings.nwcWallets
                        .sample(1000)
                        .collectLatest { invalidateFilters() }
                },
            )

        return requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(key, relay, TimeUtils.now(), forFilters)
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) newEose(key, relay, TimeUtils.now(), forFilters)

                    val notification = event as? NwcNotificationEvent ?: return
                    if (seen.put(notification.id, true) == null) {
                        key.account.scope.launch(Dispatchers.IO) {
                            key.account.nip47SignerState.handleIncomingNotification(notification)
                        }
                    }
                }
            },
        )
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
    }
}
