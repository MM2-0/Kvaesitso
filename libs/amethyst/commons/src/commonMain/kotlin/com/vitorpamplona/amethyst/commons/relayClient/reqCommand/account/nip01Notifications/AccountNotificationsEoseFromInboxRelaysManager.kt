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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterNotificationsToPubkeys
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterSummaryNotificationsToPubkeys
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.MergedAuthorTracker
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The live notification tail, for **every** logged-in account, in one subscription.
 *
 * Notifications are `#p`-scoped, so a relay that serves several of the user's accounts can be asked
 * about all of them in one filter naming every pubkey — the query is identical in shape, only wider.
 * That is what keeps this within a relay's `max_subscriptions`: one REQ per relay instead of one per
 * (account, relay). With four accounts open, the per-account form pushed strfry relays past their
 * 20-subscription cap and they answered `ERROR: too many concurrent REQs` — a NOTICE, carrying no
 * subscription id, so the client could not even tell which REQ had been dropped. Those filters stayed
 * "live" in our books and silently never delivered.
 *
 * Gift wraps deliberately do **not** merge this way. They are unsolicited and their content is opaque
 * to the relay, so it cannot rate-limit them per recipient; a merged query would let one spammed
 * account consume the shared `limit` and starve every other account's DMs. Each account keeps its own
 * budget there — see [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager].
 *
 * ## The `limit` here is shared, and deliberately not scaled
 *
 * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata.AccountMetadataEoseManager]
 * multiplies its limits by the number of merged accounts; this one does not, and the difference is the
 * kind of event. Metadata is replaceable, so the limit is a safety bound and widening it costs nothing.
 * Notifications are a stream, so the limit is a page size: scaling it by four accounts would ask four
 * times the data of every relay on every cold start, and most would clamp it anyway (`max_limit` is 500
 * on strfry — already below the 2000 the summary filter asks for).
 *
 * So on a cold start a busy account can crowd a quiet one out of the shared newest-N. What recovers it
 * is [AccountNotificationsHistoryEoseManager], which pages backward per account and stays unmerged for
 * exactly that reason.
 */
class AccountNotificationsEoseFromInboxRelaysManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : SingleSubEoseManager<AccountQueryState>(client, allKeys) {
    override fun distinct(key: AccountQueryState) = key.account.userProfile()

    /**
     * One filter set per inbox relay, naming every account that reads from it.
     *
     * The `since` floor is per relay rather than per account, because the merged filter is per relay:
     * the **oldest** of the participating accounts' floors wins, so widening the query for one account
     * can never cut another one short.
     */
    override fun updateFilter(
        keys: List<AccountQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val accountsPerRelay = mutableMapOf<NormalizedRelayUrl, MutableList<AccountQueryState>>()
        keys.forEach { key ->
            key.account.notificationRelays.flow.value.forEach { relay ->
                accountsPerRelay.getOrPut(relay) { mutableListOf() }.add(key)
            }
        }

        return accountsPerRelay.flatMap { (relay, accounts) ->
            val pubkeys = accounts.map { it.account.userProfile().pubkeyHex }

            // An account that joins a relay this subscription already covers would otherwise inherit
            // the cursor the earlier accounts earned, and never ask for anything older than it. Drop
            // the stored cursor AND ignore it for this pass — not just the former, which would leave
            // the refetch depending on `since` being a live view of the map we just mutated.
            val gained = authorsPerRelay.gainedAuthors(relay, pubkeys)
            if (gained) clearEoseFor(relay)

            // A cold-start floor, NOT paging — backward paging lives in
            // [AccountNotificationsHistoryEoseManager]. Read only when a relay has no EOSE yet; once it
            // does, the EOSE time wins and this is never consulted.
            //
            // `since` means *newer than*, so this floors the query at the depth the feed already reaches
            // rather than asking all-time again. It stays null until the feed holds a full page — and a
            // background account has no feed at all (no screen ever mounted one), which is why the key
            // carries none and this reads null there. Null for ANY account on the relay means no floor
            // at all, since a floor derived from one account's feed would truncate the others'.
            val floors = accounts.map { it.feedContentStates?.notifications?.lastNoteCreatedAtIfFilled() }
            val pagingBoundary = if (floors.any { it == null }) null else floors.filterNotNull().min()

            // No `since` floor on the first fetch. These filters are scoped by `#p` to my own
            // keys and carry a relay-side `limit`, so an all-time query costs one index scan and
            // returns at most `limit` events, newest first — exactly what Home does (it passes
            // `since ?: boundary`, i.e. null on a cold start).
            //
            // This used to fall back to `oneWeekAgo()`, which silently emptied the tab for
            // anyone whose last mention was older than a week: EOSE `since` is in-memory only,
            // so EVERY cold start re-pinned the window to 7 days, and the paging boundary above
            // could never rescue it — it only arms once the feed holds a full page, and the feed
            // could not fill because the query only ever asked for a week. A fresh install of an
            // established account hit the same deadlock.
            val notificationSince = (if (gained) null else since?.get(relay)?.time) ?: pagingBoundary

            // NIP-29 group activity (reactions/replies to my messages) is deliberately NOT requested
            // here. It lives on the group's host relay and used to be one `#h` filter per relay carrying
            // every joined group id — but this subscription also carries the inbox filters below, which
            // have no `#h` at all, and `block/buzz` downgrades any subscription with a channel-less (or
            // multi-channel) filter to "global", a class that by design never receives channel-scoped
            // events. So those filters answered the stored query at EOSE and then went deaf until the
            // next launch.
            //
            // Group and Buzz-DM activity now rides the per-channel subscriptions that are already scoped
            // to exactly one channel — RelayGroupJoinedChatTailSubAssembler and
            // BuzzDmJoinedChatTailSubAssembler, both mounted app-wide from LoggedInPage, so coverage is
            // unchanged and delivery is live.
            filterSummaryNotificationsToPubkeys(relay = relay, pubkeys = pubkeys, since = notificationSince) +
                filterNotificationsToPubkeys(relay = relay, pubkeys = pubkeys, since = notificationSince)
        }
    }

    /**
     * Per-account watchers, rebuilt as accounts come and go.
     *
     * There is only one subscription now, so these cannot hang off a per-key `newSub`. They are keyed
     * by user and reconciled here: an account that leaves has its jobs cancelled, and one that arrives
     * gets its own. Re-entrancy is safe — the watchers call `invalidateFilters()`, which lands back
     * here and finds every account already watched.
     */
    private val authorsPerRelay = MergedAuthorTracker()

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun updateSubscriptions(keys: Set<AccountQueryState>) {
        val wanted = keys.associateBy { it.account.userProfile() }

        (userJobMap.keys - wanted.keys).toList().forEach { user ->
            userJobMap.remove(user)?.forEach { it.cancel() }
        }

        wanted.forEach { (user, key) ->
            if (user !in userJobMap) {
                userJobMap[user] =
                    listOf(
                        key.account.scope.launch(Dispatchers.IO) {
                            key.account.notificationRelays.flow.sample(1000).collectLatest {
                                invalidateFilters()
                            }
                        },
                    ) +
                    // Only a screen can fill a feed, so there is nothing to watch for a
                    // background account.
                    listOfNotNull(
                        key.feedContentStates?.let { feeds ->
                            key.account.scope.launch(Dispatchers.IO) {
                                feeds.notifications.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
                                    invalidateFilters()
                                }
                            }
                        },
                    )
            }
        }

        super.updateSubscriptions(keys)
    }

    override fun destroy() {
        authorsPerRelay.clear()
        userJobMap.values.forEach { jobs -> jobs.forEach { it.cancel() } }
        userJobMap.clear()
        super.destroy()
    }
}
