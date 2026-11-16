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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.buzz

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.MembershipNotificationKinds
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Everything the workspace relay addresses to me personally: the membership verdicts plus the kind-30622
 * snapshot of which DMs I have hidden. One filter class — `#p` = me, channel-less, workspace relay — so
 * they share a subscription.
 */
private val WorkspaceInboxKinds = MembershipNotificationKinds + DmVisibilityEvent.KIND

/**
 * One workspace relay's worth of "things addressed to me personally": membership verdicts and my
 * hidden-DM snapshot, `#p` = me.
 *
 * Empty for a missing pubkey so a not-yet-loaded account asks for nothing rather than for everyone's.
 */
fun filterWorkspaceInboxToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey.isNullOrEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.NOTIFICATIONS,
                    accountPubKeys = listOf(pubkey),
                    kinds = WorkspaceInboxKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    since = since,
                ),
        ),
    )
}

/**
 * Always-on read of the Buzz relay's membership notifications addressed to me (`#p` = me) across every
 * joined workspace — the events behind the "somebody added you to a channel" prompts and behind Buzz DM
 * discovery.
 *
 * On a Buzz relay membership is server-side: another member issues the add, the relay writes me into the
 * channel's kind-39002 roster, and then addresses me a kind-44100 naming the actor. There is no queryable
 * channel list, so this `#p`-gated stream *is* the enumeration; the matching kind-44101 withdraws one.
 *
 * ### Why its own subscription
 *
 * This filter is channel-less by nature — it is the query that *discovers* which channels exist for me,
 * so there is no `#h` to scope it with. `block/buzz` downgrades any subscription carrying a channel-less
 * (or multi-channel) filter to "global", a class that by design never receives channel-scoped events;
 * that is exactly why NIP-29 group activity was moved out of
 * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications.AccountNotificationsEoseFromInboxRelaysManager]
 * and onto the per-channel tails. Global is the correct class for 44100/44101 — they are stored globally
 * so their target can find them — but only as long as nothing channel-scoped shares the subscription. So
 * these ride a manager of their own rather than joining the inbox notification filters.
 *
 * Also note the relays: workspace relays are not the account's `notificationRelays`, so the inbox manager
 * would not query them even if the filter class allowed it.
 *
 * ### Auth
 *
 * The read is `#p`-gated, so the relay answers `auth-required:`. A joined workspace is first-party, so
 * each one is pre-approved on the account's auth ledger here — the restore-from-disk path doesn't set
 * that, unlike the invite/import/console entry points. [com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator]
 * re-signs on the refusal using the connection's stored challenge and the OK re-drives the REQ, so the
 * subscription recovers on its own rather than needing a warm-auth one-shot fetch.
 */
class BuzzMembershipEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val me = key.account.userProfile().pubkeyHex

        // No `since` floor on a cold start. LocalCache is in-memory, so a relaunch has to re-read the
        // whole membership history to know which channels I am in — and the EOSE map that would supply
        // a floor is in-memory too, so it is null exactly when the full read is needed. The filter is
        // `#p`-scoped to my own key, so an all-time query costs one index scan.
        return key.account.buzzWorkspaces.flow.value.flatMap { relay ->
            filterWorkspaceInboxToPubkey(relay, me, since?.get(relay)?.time)
        }
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }

        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.buzzWorkspaces.flow.collectLatest { relays ->
                        // Idempotent, and re-run per joined set rather than once at mount so a workspace
                        // joined later is pre-approved too.
                        relays.forEach { key.account.relayAuthLedger.setDecision(it.url, RelayAuthDecision.ALLOW) }
                        invalidateFilters()
                    }
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
