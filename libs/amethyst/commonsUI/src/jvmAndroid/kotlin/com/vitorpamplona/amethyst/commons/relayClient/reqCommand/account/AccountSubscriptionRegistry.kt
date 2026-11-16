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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log

/**
 * Mounts the account-level loaders — notifications, DMs, gift wraps, drafts,
 * metadata — for accounts that have no screen of their own.
 *
 * Every other mount of [AccountFilterAssembler] comes from a composable holding an
 * `AccountViewModel`, which means it only ever covers the account the user is
 * looking at. Every other logged-in account was merely resident in memory so pushed
 * gift wraps could be decrypted by their owner; everything else about them depended
 * on a push message arriving. On a device with no push (no Play Services, no
 * UnifiedPush distributor, Pokey not installed) they pulled nothing at all.
 *
 * This registry is the pull side. It holds one [AccountQueryState] per account it is
 * given and drives [AccountFilterAssembler.subscribe] /
 * [AccountFilterAssembler.unsubscribe] directly — those are plain functions, not
 * composables, so no UI has to exist.
 *
 * The keys carry no feed states (see [AccountQueryState.feedContentStates]) and no
 * `otherAccounts` — populating the account switcher's avatars is a screen's job, and
 * these accounts have no screen.
 *
 * It deliberately decides nothing about *which* accounts those are: it mounts exactly
 * the set it is handed, so the foreground/background rule lives in one place, in
 * the Android app's `AlwaysOnNotificationServiceManager`,
 * which already watches the switches that define it and calls [sync] on every change.
 */
class AccountSubscriptionRegistry(
    private val assembler: AccountFilterAssembler,
) {
    companion object {
        private const val TAG = "AccountSubscriptions"
    }

    private val mounted = mutableMapOf<HexKey, AccountQueryState>()

    /**
     * Makes the mounted set match [accounts] exactly: subscribes the ones that just
     * joined it, unsubscribes the ones that left or were unloaded, and leaves the
     * rest untouched.
     *
     * Idempotent, so callers can hand it the same set on every emission of the flows
     * behind it without churning subscriptions.
     */
    @Synchronized
    fun sync(accounts: Collection<Account>) {
        val wanted = accounts.associateBy { it.userProfile().pubkeyHex }

        // Unmount accounts that dropped out, and accounts whose Account object was
        // replaced (re-login rebuilds it) — the stale instance holds the old
        // signer and relay lists, so its filters would be built from dead state.
        val stale = mounted.filter { (pubkey, state) -> wanted[pubkey] !== state.account }
        stale.forEach { (pubkey, state) ->
            mounted.remove(pubkey)
            assembler.unsubscribe(state)
        }

        var added = 0
        wanted.forEach { (pubkey, account) ->
            if (pubkey !in mounted) {
                val state = AccountQueryState(account, emptySet())
                mounted[pubkey] = state
                assembler.subscribe(state)
                added++
            }
        }

        if (added > 0 || stale.isNotEmpty()) {
            Log.d(TAG) { "Account subscriptions: ${mounted.size} mounted (+$added, -${stale.size})" }
        }
    }

    /** Unmounts everything. Used when the app goes away with the master off, and on logout. */
    @Synchronized
    fun clear() = sync(emptyList())
}
