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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterBasicAccountInfoFromKeys
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterBookmarksAndReportsFromKey
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterDrssFromKey
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterFollowsAndMutesFromKey
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterLastPostsFromKey
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.MergedAuthorTracker
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Which relays to ask for an account's own events, in order of what we actually know.
 *
 * Split out from the manager so the precedence can be tested without standing up an account:
 * the rule is the whole fix, and the case that broke — every set but [defaults] empty — is the
 * one no live account reproduces on demand.
 */
fun pickAccountDataRelays(
    home: Set<NormalizedRelayUrl>,
    inbox: Set<NormalizedRelayUrl>,
    defaults: Set<NormalizedRelayUrl>,
): Set<NormalizedRelayUrl> =
    when {
        home.isNotEmpty() -> home
        inbox.isNotEmpty() -> inbox
        else -> defaults
    }

/**
 * Each account's own profile, lists and recent posts — for **every** logged-in account, in one
 * subscription.
 *
 * Every filter here is `authors`-keyed, so a relay that several accounts read from can be asked
 * about all of them at once by widening `authors` rather than opening a REQ per account. This was
 * the largest single contributor to blowing a relay's `max_subscriptions`: seven filters in a
 * subscription, repeated once per account.
 *
 * The per-account `limit`s are summed rather than shared. These are mostly replaceable events, so
 * the limit is a safety bound rather than a page size, and scaling it by the number of accounts
 * keeps each one exactly the headroom it had alone.
 */
class AccountMetadataEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : SingleSubEoseManager<AccountQueryState>(client, allKeys) {
    override fun distinct(key: AccountQueryState) = key.account.userProfile()

    fun relayFlow(query: AccountQueryState) = query.account.homeRelays.flow

    /**
     * Where to look for an account's **own** profile, follow list and lists.
     *
     * Normally its home relays: where it publishes is where its own events are. But home relays
     * are NIP-65 write relays (plus private storage and local ones), and a kind:10002 that lists
     * none — read-only, or simply wrong — is a statement about publishing, not about where the
     * account's existing events can be found. Keyed on that set alone, such an account joined no
     * relay bucket at all and this subscription asked **nothing** on its behalf: its profile,
     * follows and every backed-up list stayed frozen at whatever was last stored, forever, with
     * nothing on screen to say why. A follow list replaced from another client never arrived, so
     * the backup guard could not even raise the conflict it exists to raise.
     *
     * The fallbacks only widen where we *read*; they never redirect a publish. Its inbox relays
     * come first — a client that rewrote these lists most likely also put them where the account
     * says to reach it — and the app's event finders last, which is what "we don't know" means
     * everywhere else.
     *
     * Deliberately not fixed in [relayListOrDefaultsWhenUnknown]: an empty list there means "they
     * told us: nothing", and defaulting it would override an explicit choice about publishing.
     */
    fun relaysToQuery(query: AccountQueryState): Set<NormalizedRelayUrl> =
        pickAccountDataRelays(
            home = relayFlow(query).value,
            inbox = query.account.nip65RelayList.inboxFlow.value,
            defaults = Constants.eventFinderRelays,
        )

    override fun updateFilter(
        keys: List<AccountQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val accountsPerRelay = mutableMapOf<NormalizedRelayUrl, MutableList<AccountQueryState>>()
        keys.forEach { key ->
            relaysToQuery(key).forEach { relay ->
                accountsPerRelay.getOrPut(relay) { mutableListOf() }.add(key)
            }
        }

        return accountsPerRelay.flatMap { (relay, accounts) ->
            val pubkeys = accounts.map { it.account.userProfile().pubkeyHex }

            // An account joining a relay this subscription already covers must not inherit the cursor
            // the earlier accounts earned — it would never ask for its own profile, follows or lists.
            // This matters more here than for notifications: there is no backward pager to rescue it,
            // so the account would go without until the next launch cleared the in-memory cursor.
            // Drop the stored cursor AND ignore it for this pass, so the refetch does not depend on
            // `since` being a live view of the map we just mutated.
            val gained = authorsPerRelay.gainedAuthors(relay, pubkeys)
            if (gained) clearEoseFor(relay)

            val relaySince = if (gained) null else since?.get(relay)?.time

            // The account-switcher avatars: other logged-in accounts this screen wants to name.
            // Screens supply them; the background registry does not, so this is usually empty.
            val otherAccounts = accounts.flatMapTo(mutableSetOf()) { it.otherAccounts }.minus(pubkeys.toSet())

            listOf(
                filterAccountInfoAndListsFromKey(relay, pubkeys, relaySince),
                filterFollowsAndMutesFromKey(relay, pubkeys, relaySince),
                filterBookmarksAndReportsFromKey(relay, pubkeys, relaySince),
                filterDrssFromKey(relay, pubkeys, relaySince),
                filterLastPostsFromKey(relay, pubkeys, relaySince ?: TimeUtils.oneMonthAgo()),
                filterBasicAccountInfoFromKeys(relay, otherAccounts.toList(), relaySince, pubkeys),
            ).flatten()
        }
    }

    private val authorsPerRelay = MergedAuthorTracker()

    /** Per-account relay watchers, reconciled as accounts come and go. See the notifications manager. */
    private val userJobMap = mutableMapOf<User, List<Job>>()

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
                            relayFlow(key).collectLatest { invalidateFilters() }
                        },
                        // The inbox list is watched too because [relaysToQuery] falls back to it:
                        // an account with no home relays would otherwise keep querying whatever
                        // the fallback resolved to at startup, deaf to its own NIP-65 changing.
                        key.account.scope.launch(Dispatchers.IO) {
                            key.account.nip65RelayList.inboxFlow
                                .collectLatest { invalidateFilters() }
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
