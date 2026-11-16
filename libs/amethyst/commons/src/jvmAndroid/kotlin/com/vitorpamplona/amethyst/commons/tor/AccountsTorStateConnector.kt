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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Pushes the relay classifications [TorRelayState] needs — which relays are DM, trusted, guessed,
 * money-operation or remote-signer relays — as a union across every logged-in account.
 *
 * All five are the same fold: pick one set per account, union them, publish. It used to be written
 * out four times at ~30 lines each, and the copies had already drifted apart in trivial ways (an
 * `if (isEmpty)` guard that could never fire, differently-named accumulators). Sharing one
 * implementation is what keeps the next classification from being another 30 lines of the same
 * thing — and, more importantly, from being 30 lines that quietly forget a step.
 */
class AccountsTorStateConnector(
    accountsCache: AccountCacheState,
    torEvaluatorFlow: TorRelayState,
    scope: CoroutineScope,
) {
    /**
     * Union of one relay set across all logged-in accounts, republished into [TorRelayState].
     *
     * `debounce(200)` rides out the burst of account churn at login; `transformLatest` drops the
     * previous fan-in when the account set changes so a logged-out account cannot keep contributing.
     * The seed is `emptySet()` for every classification: before any account exists, nothing is
     * classified.
     *
     * Takes its collaborators as parameters rather than reading constructor properties because the
     * call sites are property initializers, where non-`val` constructor parameters are in scope but
     * member functions cannot see them.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun unionAcrossAccounts(
        accountsCache: AccountCacheState,
        scope: CoroutineScope,
        select: (Account) -> Flow<Set<NormalizedRelayUrl>>,
        publish: (Set<NormalizedRelayUrl>) -> Unit,
    ): StateFlow<Set<NormalizedRelayUrl>> =
        accountsCache.accounts
            .debounce(200)
            .transformLatest { snapshot ->
                val perAccount =
                    snapshot
                        .map { select(it.value) }
                        .ifEmpty { listOf(MutableStateFlow(emptySet())) }

                emitAll(
                    combine(perAccount) { sets ->
                        sets.flatMapTo(mutableSetOf()) { it }
                    },
                )
            }.onEach(publish)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /** NIP-17 DM relays: these follow the dedicated DM preference, never the generic "new" one. */
    val allDmRelayFlows: StateFlow<Set<NormalizedRelayUrl>> =
        unionAcrossAccounts(
            accountsCache,
            scope,
            select = { it.dmRelayList.flow },
            publish = { torEvaluatorFlow.dmRelays.tryEmit(it) },
        )

    /** Everything the user actually put in one of their own relay lists. */
    val allTrustedRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> =
        unionAcrossAccounts(
            accountsCache,
            scope,
            select = { it.trustedRelays.flow },
            publish = { torEvaluatorFlow.trustedRelays.tryEmit(it) },
        )

    /**
     * Relays the app is *guessing* while an account's own lists are unknown. Feeds
     * [TorRelayState.assumedRelays] and nothing else — see `AssumedRelayListsState` for why these
     * must never reach the AUTH decision.
     *
     * Per account, so a second login cannot re-open the guess for an established one; each
     * account's contribution empties itself as soon as that account's own lists land.
     */
    val allAssumedRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> =
        unionAcrossAccounts(
            accountsCache,
            scope,
            select = { it.assumedRelays.flow },
            publish = {
                logHandover(it)
                torEvaluatorFlow.assumedRelays.tryEmit(it)
            },
        )

    /**
     * Persistent money-operation relays: NIP-47 wallet relays and saved CLINK Debits service
     * relays, so these connections honor the money-operations preference rather than being
     * classified as generic "new" relays.
     */
    val allMoneyOpRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> =
        unionAcrossAccounts(
            accountsCache,
            scope,
            select = { account ->
                combine(
                    account.settings.nwcWallets,
                    account.settings.clinkDebitWallets,
                ) { nwcWallets, clinkDebitWallets ->
                    val relays = mutableSetOf<NormalizedRelayUrl>()
                    nwcWallets.forEach { relays.add(it.uri.relayUri) }
                    clinkDebitWallets.forEach { relays.addAll(it.pointer.relays) }
                    relays.toSet()
                }
            },
            publish = { torEvaluatorFlow.moneyOpRelays.tryEmit(it) },
        )

    /**
     * NIP-46 relays each account reaches its remote signer on, from its saved bunker address. They
     * follow the trusted-relay preference instead of the new-relay one, which put every signature
     * behind Tor.
     */
    val allSignerRelaysFlow: StateFlow<Set<NormalizedRelayUrl>> =
        unionAcrossAccounts(
            accountsCache,
            scope,
            select = { account ->
                // Re-read on every settings save: a signer that moves (NIP-46 switch_relays)
                // rewrites the saved address.
                account.settings.saveable
                    .map {
                        account.settings.remoteSignerBunkerUri
                            ?.let { NostrConnectURI.parseBunker(it)?.relays }
                            ?: emptySet()
                    }.distinctUntilChanged()
            },
            publish = { torEvaluatorFlow.signerRelays.tryEmit(it) },
        )

    @Volatile private var lastAssumedCount: Int = -1

    /**
     * The handover is the whole contract of the guessed-relay feature: the moment a user's own
     * lists arrive, every relay we were guessing about goes back to the policy they actually asked
     * for. Logged at INFO because "did it hand over, and when" is not answerable from any other
     * line — the reconnect that follows looks identical to an ordinary one.
     */
    private fun logHandover(relays: Set<NormalizedRelayUrl>) {
        if (relays.size == lastAssumedCount) return
        val released = if (relays.isEmpty()) " (own lists arrived; released to their real Tor policy)" else ""
        Log.i("AccountsTorState") { "Guessed relays: $lastAssumedCount -> ${relays.size}$released" }
        lastAssumedCount = relays.size
    }
}
