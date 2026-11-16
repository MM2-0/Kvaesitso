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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient

/**
 * Amethyst variant of [SingleSubNoEoseCacheEoseManager] that restores single-account
 * attribution for [AccountScopedQuery] keys.
 *
 * The commons base is account-agnostic (attribution defaults to null) so it can live in
 * commonMain. Query states that carry an [Account] (home feed, channels, notifications, …)
 * subclass this so their single-account REQs still show up attributed in "Active Relay
 * Subscriptions".
 *
 * Keyed on [AccountScopedQuery] rather than a concrete query-state type: the home feed uses
 * HomeQueryState, notifications use AccountQueryState, and checking one concrete class filed the
 * other under "not attributed" despite both being built from a single account's data.
 */
abstract class AccountScopedSingleSubNoEoseCacheEoseManager<T>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    invalidateAfterEose: Boolean = false,
) : SingleSubNoEoseCacheEoseManager<T>(client, allKeys, invalidateAfterEose) {
    override fun accountPubKeyOf(key: Any?): String? = (key as? AccountScopedQuery)?.account?.userProfile()?.pubkeyHex
}
