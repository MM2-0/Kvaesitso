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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FollowActionsTest {
    private val myPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val mySigner = NostrSignerInternal(KeyPair(myPriv.hexToByteArray()))

    // Pre-computed 32-byte (x-only pubkey) hexes — content doesn't matter, only
    // length + uniqueness. NIP-02 verification is lenient about these being
    // real curve points.
    private val alice = "1111111111111111111111111111111111111111111111111111111111111111"
    private val bob = "2222222222222222222222222222222222222222222222222222222222222222"
    private val carol = "3333333333333333333333333333333333333333333333333333333333333333"

    @Test
    fun followFromScratch_createsKind3WithSinglePubkey() =
        runTest {
            val event = FollowActions.buildFollow(mySigner, alice, currentContactList = null)

            assertEquals(ContactListEvent.KIND, event.kind)
            assertEquals(mySigner.pubKey, event.pubKey)
            assertEquals(setOf(alice), event.verifiedFollowKeySet())
        }

    @Test
    fun followFromExistingList_appendsWithoutLosingPriorFollows() =
        runTest {
            val initial = FollowActions.buildFollow(mySigner, alice, currentContactList = null)

            val updated = FollowActions.buildFollow(mySigner, bob, currentContactList = initial)

            assertEquals(setOf(alice, bob), updated.verifiedFollowKeySet())
            // New event must replace the old one (different id), not no-op back.
            assertTrue(updated.id != initial.id)
        }

    @Test
    fun followAlreadyFollowed_isNoOp() =
        runTest {
            val initial = FollowActions.buildFollow(mySigner, alice, currentContactList = null)

            val redundant = FollowActions.buildFollow(mySigner, alice, currentContactList = initial)

            // Underlying builder short-circuits to the same event.
            assertSame(initial, redundant)
        }

    @Test
    fun unfollowFromExistingList_removesOnlyTargetTag() =
        runTest {
            val twoFollows =
                FollowActions.buildFollowBatch(
                    signer = mySigner,
                    pubkeysWithHints = listOf(alice to null, bob to null),
                    currentContactList = null,
                )
            assertEquals(setOf(alice, bob), twoFollows.verifiedFollowKeySet())

            val removed = FollowActions.buildUnfollow(mySigner, alice, currentContactList = twoFollows)

            assertNotNull(removed)
            assertEquals(setOf(bob), removed.verifiedFollowKeySet())
        }

    @Test
    fun unfollowWithNullCurrent_returnsNull() =
        runTest {
            val result = FollowActions.buildUnfollow(mySigner, alice, currentContactList = null)
            assertNull(result, "no prior list means nothing to unfollow — caller should treat as no-op")
        }

    @Test
    fun unfollowNonMember_returnsEventWithSameId() =
        runTest {
            val onlyAlice = FollowActions.buildFollow(mySigner, alice, currentContactList = null)

            // Builder short-circuits when the pubkey isn't tagged — we get the
            // same event back, so callers can detect no-op by id equality.
            val result = FollowActions.buildUnfollow(mySigner, bob, currentContactList = onlyAlice)

            assertNotNull(result)
            assertEquals(onlyAlice.id, result.id)
        }

    @Test
    fun followBatchFromScratch_createsKind3WithAllPubkeys() =
        runTest {
            val event =
                FollowActions.buildFollowBatch(
                    signer = mySigner,
                    pubkeysWithHints = listOf(alice to null, bob to null, carol to null),
                    currentContactList = null,
                )

            assertEquals(setOf(alice, bob, carol), event.verifiedFollowKeySet())
        }

    @Test
    fun followBatchOnExistingList_unionsWithoutLosingPriorFollows() =
        runTest {
            val initial = FollowActions.buildFollow(mySigner, alice, currentContactList = null)

            val updated =
                FollowActions.buildFollowBatch(
                    signer = mySigner,
                    pubkeysWithHints = listOf(bob to null, carol to null),
                    currentContactList = initial,
                )

            assertEquals(setOf(alice, bob, carol), updated.verifiedFollowKeySet())
        }
}
