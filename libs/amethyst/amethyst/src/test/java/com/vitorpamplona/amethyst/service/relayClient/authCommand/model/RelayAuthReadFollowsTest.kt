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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "…I'm reading someone I follow" has to actually cover the relays it is about.
 *
 * The whole point of the toggle is the outbox relay of somebody else — a relay we do not publish to,
 * do not read our own inbox from, and do not list. That is exactly the shape `isFirstParty` reports
 * false for, so requiring it emptied the category: with the toggle explicitly on, every one of the
 * user's follows still produced a login prompt for its outbox relay.
 */
class RelayAuthReadFollowsTest {
    private val followsRelay = "wss://outbox.someone-i-follow.example/"
    private val followed = "a".repeat(64)
    private val stranger = "b".repeat(64)

    private class NoStore : RelayAuthPermissionStore {
        override suspend fun loadDecision(relayUrl: String): RelayAuthDecision? = null

        override suspend fun storeDecision(
            relayUrl: String,
            decision: RelayAuthDecision,
        ) = Unit

        override suspend fun clearDecision(relayUrl: String) = Unit

        override suspend fun allDecisions(): Map<String, RelayAuthDecision> = emptyMap()
    }

    private fun ledger(toggles: RelayAuthCustomToggles = RelayAuthCustomToggles()) =
        RelayAuthPermissionLedger(
            store = NoStore(),
            globalPolicy = { RelayAuthPolicy.CUSTOM },
            sessionGrants = RelayAuthSessionGrants(),
            customToggles = { toggles },
            isFollowed = { it == followed },
        )

    private fun readOutbox(vararg authors: String) = RelayAuthContext(followsRelay, listOf(AuthPurpose(AuthPurposeKind.READ_OUTBOX, authors.toSet())))

    @Test
    fun readingAFollowAutoAuthenticatesOnTheirOwnOutboxRelay() =
        runTest {
            // isFirstParty = false is not an edge case here, it is *the* case: the relay belongs to the
            // author we are reading. Before the fix this returned ASK, so a user on "decide per relay"
            // with this toggle on was prompted once per follow.
            assertEquals(
                RelayAuthVerdict.ALLOW,
                ledger().decide(readOutbox(followed), isFirstParty = false),
            )
        }

    @Test
    fun readingAFollowStillAsksWhenTheToggleIsOff() =
        runTest {
            val off = RelayAuthCustomToggles(readFollows = false)
            assertEquals(
                RelayAuthVerdict.ASK,
                ledger(off).decide(readOutbox(followed), isFirstParty = false),
            )
        }

    @Test
    fun readingAStrangerStillAsks() =
        runTest {
            // There is deliberately no "read strangers" category — browsing a profile we don't follow
            // on a relay of theirs is still a question.
            assertEquals(
                RelayAuthVerdict.ASK,
                ledger().decide(readOutbox(stranger), isFirstParty = false),
            )
        }

    @Test
    fun oneFollowInABatchedReadIsEnough() =
        runTest {
            // Outbox reads are batched per relay, so a single filter routinely names a mix. One
            // followed author in it is the reason we are on this relay at all.
            assertEquals(
                RelayAuthVerdict.ALLOW,
                ledger().decide(readOutbox(stranger, followed), isFirstParty = false),
            )
        }

    @Test
    fun messagingIsNotCoveredByTheReadExemption() =
        runTest {
            // Delivering to a followed user's *inbox* keeps the first-party gate: the pending event
            // would be ours, and when it isn't, the traffic belongs to another logged-in account.
            val ctx =
                RelayAuthContext(
                    followsRelay,
                    listOf(AuthPurpose(AuthPurposeKind.SEND_DM, setOf(followed))),
                )
            assertEquals(RelayAuthVerdict.ASK, ledger().decide(ctx, isFirstParty = false))
            assertEquals(RelayAuthVerdict.ALLOW, ledger().decide(ctx, isFirstParty = true))
        }
}
