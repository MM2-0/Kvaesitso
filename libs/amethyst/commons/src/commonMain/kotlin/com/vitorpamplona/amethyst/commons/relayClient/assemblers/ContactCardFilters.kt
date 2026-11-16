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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent

val UserAssertionKindList = listOf(UserAssertionEvent.KIND)

/**
 * Kind:30382 cards *about* [targets], written by [trustedAccounts] (the account
 * itself plus its WoT trust providers). Fetches nicknames and scores for the
 * users currently on screen.
 */
fun filterUserAssertionsToTargetKeysFromTrustedAccountsInTheRelay(
    targets: Set<HexKey>,
    trustedAccounts: List<HexKey>,
    relay: NormalizedRelayUrl,
    since: Long?,
    accountPubKey: HexKey? = null,
): RelayBasedFilter? {
    if (targets.isEmpty() || trustedAccounts.isEmpty()) return null
    return RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.PROFILE_METADATA,
                accountPubKeys = listOfNotNull(accountPubKey),
                kinds = UserAssertionKindList,
                authors = trustedAccounts,
                // kind:30382 addresses the target user in the d-tag
                tags = mapOf(DTag.TAG_NAME to targets.sorted()),
                since = since,
            ),
    )
}

/**
 * Every kind:30382 card *written by* [authors] — the accounts' own nicknames —
 * for the bulk download at login from the accounts' own relays. Addressable events:
 * one card per target user, hence the larger limit.
 *
 * [SubPurpose.ACCOUNT_DATA], not [SubPurpose.PROFILE_METADATA] like its sibling above: nobody has to
 * be on screen for this to run. It is part of the login-time account load, so filing it under
 * "Observing Profiles" — explained as *"profiles of the people currently on screen"* — made every
 * logged-in account look like it was watching somebody.
 */
fun filterUserAssertionsByAuthorInTheRelay(
    relay: NormalizedRelayUrl,
    authors: List<HexKey>,
    since: Long?,
    limit: Int = 500,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.ACCOUNT_DATA,
                // This variant fetches the accounts' OWN contact cards, so the authors are the owners.
                accountPubKeys = authors,
                kinds = UserAssertionKindList,
                authors = authors,
                limit = limit,
                since = since,
            ),
    )
