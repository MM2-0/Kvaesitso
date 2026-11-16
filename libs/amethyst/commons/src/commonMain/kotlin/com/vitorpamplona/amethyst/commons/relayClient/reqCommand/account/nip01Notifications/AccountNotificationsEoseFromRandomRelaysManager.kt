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
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterJustTheLatestNotificationsToPubkeyFromRandomRelays
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountUiQueryState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class AccountNotificationsEoseFromRandomRelaysManager(
    client: INostrClient,
    allKeys: () -> Set<AccountUiQueryState>,
) : PerUserEoseManager<AccountUiQueryState>(client, allKeys) {
    override fun user(key: AccountUiQueryState) = key.account.userProfile()

    /**
     * Most notifications arrive on the user's own inbox relays. This is the straggler probe for the
     * rest: other clients sometimes deliver a mention to the author's own relays instead of the
     * recipient's inbox, so those only ever turn up if we go and look.
     *
     * It looks at a **rotating window of [RELAYS_PER_PASS] relays**, not at every relay the follows
     * post to. The whole set was ~330 relays on a normal account, and subscribing to all of them
     * held roughly 670 filters permanently — by a wide margin the largest thing the client ran, for
     * a job that only needs to sweep the space eventually, not watch all of it at once. The window
     * slides every [PASS_DURATION_MS], so every relay is still visited, just never all at the same
     * time.
     *
     * The window is a contiguous slice of a URL-sorted list rather than a random draw: a fresh
     * random pick on each invalidation would re-REQ a different set every few seconds, which costs
     * more than the subscriptions it replaced. Deterministic order means the window only moves when
     * the rotation timer says so.
     */
    override fun updateFilter(
        key: AccountUiQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // only loads this after the feed is built, so it stays null on a quiet inbox. No week floor
        // behind it: this probe is `#p`-scoped to me with `limit = 20`, so relays answer with the 20
        // newest either way — the floor only ever hid notifications older than a week, and since the
        // boundary above needs a full page to arm, a quiet inbox could never page past it.
        val defaultSince = key.feedContentStates.notifications.lastNoteCreatedAtIfFilled()
        val candidates =
            (key.account.followsPerRelay.value.keys - key.account.notificationRelays.flow.value)
                .sortedBy { it.url }
        if (candidates.isEmpty()) return emptyList()

        val start = (passIndex * RELAYS_PER_PASS).mod(candidates.size)
        val window = List(minOf(RELAYS_PER_PASS, candidates.size)) { candidates[(start + it).mod(candidates.size)] }

        return window.flatMap {
            val since = since?.get(it)?.time ?: defaultSince
            filterJustTheLatestNotificationsToPubkeyFromRandomRelays(it, user(key).pubkeyHex, since)
        }
    }

    /**
     * Which slice of the sorted relay list the current pass is on. Bumped by the rotation job below;
     * `mod` at the read site keeps it valid however far it runs.
     */
    @Volatile private var passIndex = 0

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountUiQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    // no need to hurry here. we can wait the app stabilize
                    key.account.followsPerRelay.debounce(5000).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedContentStates.notifications.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
                        invalidateFilters()
                    }
                },
                // Slides the window. Cancelled with the others in endSub, so it stops with the account.
                key.account.scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(PASS_DURATION_MS)
                        passIndex++
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

    companion object {
        /**
         * Relays watched per pass. Small on purpose: this is a background sweep for misdelivered
         * mentions, and the inbox relays carry the real traffic.
         */
        const val RELAYS_PER_PASS = 5

        /** How long a window stays put before sliding. Long enough that re-REQ churn stays negligible. */
        const val PASS_DURATION_MS = 5 * 60 * 1000L
    }
}
