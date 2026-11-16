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
package com.vitorpamplona.amethyst.commons.relayClient.gitDashboard

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * The repositories the git dashboard lists as the reader's own: their announcements (from their
 * outbox), and other people's that name them in an indexed role tag (`M`, `m`, `o`), which is how
 * an ngit maintainer is invited. The legacy `maintainers` list is not indexed, so a repository
 * naming the reader only there shows once its announcement is otherwise in the cache.
 */
fun filterMyGitRepositories(
    pubkey: HexKey,
    outboxRelays: Set<NormalizedRelayUrl>,
    inboxRelays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (pubkey.isEmpty()) return emptyList()
    val me = listOf(pubkey)
    val kinds = listOf(GitRepositoryEvent.KIND)
    return outboxRelays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter = ExplainedFilter(purpose = SubPurpose.TOPIC_FEED, kinds = kinds, authors = me, since = since?.get(relay)?.time),
        )
    } +
        (outboxRelays + inboxRelays).flatMap { relay ->
            listOf("M", "m", "o").map { role ->
                RelayBasedFilter(
                    relay = relay,
                    filter = ExplainedFilter(purpose = SubPurpose.TOPIC_FEED, kinds = kinds, tags = mapOf(role to me), limit = 100, since = since?.get(relay)?.time),
                )
            }
        }
}

/** The dashboard's subscription: the reader and where their repositories live. */
class GitDashboardQueryState(
    override val account: IAccount,
    val outboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
    val inboxRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : AccountScopedQuery

class GitDashboardSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<GitDashboardQueryState>,
) : PerUserEoseManager<GitDashboardQueryState>(client, allKeys) {
    override fun user(key: GitDashboardQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: GitDashboardQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = filterMyGitRepositories(user(key).pubkeyHex, key.outboxRelays.value, key.inboxRelays.value, since)
}

/**
 * Loads the reader's repositories while the git dashboard is on screen. The items in them come
 * through each repository's own subscription (RepositoryFilterAssembler), the same one its screen
 * opens.
 */
@Stable
class GitDashboardFilterAssembler(
    client: INostrClient,
) : ComposeSubscriptionManager<GitDashboardQueryState>() {
    val group = listOf(GitDashboardSubAssembler(client, ::allKeys))

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.destroy() }
}
