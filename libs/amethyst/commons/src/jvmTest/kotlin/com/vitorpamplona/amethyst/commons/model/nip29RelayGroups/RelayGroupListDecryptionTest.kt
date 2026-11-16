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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The join/leave paths store groups as NIP-44 private items in the kind-10009
 * list (encrypted to self). This drives the exact `create`/`add`/`remove` calls
 * that [RelayGroupListState.follow] / `unfollow` delegate to, then reads them
 * back through [RelayGroupListDecryptionCache] — proving a followed group
 * survives the encrypt→sign→decrypt round-trip and that unfollow removes it.
 */
class RelayGroupListDecryptionTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val cache = RelayGroupListDecryptionCache(signer)

    private val relay = "wss://relay.example.com"
    private val groupA = GroupTag("aaaa", relay, "Alpha")
    private val groupB = GroupTag("bbbb", relay, "Beta")

    private fun keysIn(event: SimpleGroupListEvent) = runBlocking { cache.groupSet(event).map { it.groupId }.toSet() }

    @Test
    fun publicGroupIsAPlainTagAndStillReadThroughTheCache() =
        runBlocking {
            // follow() now writes public tags (interop with Flotilla/nostrord). A
            // public group must appear as a plain `["group", …]` tag AND still be
            // returned by the same merged read path used for private items.
            val list = SimpleGroupListEvent.create(publicGroups = listOf(groupA), signer = signer)

            assertTrue(list.publicGroups().any { it.groupId == "aaaa" })
            assertEquals(setOf("aaaa"), keysIn(list))
        }

    @Test
    fun readMergesPublicAndPrivateItems() =
        runBlocking {
            // A mixed list (a legacy private item + a new public one) reads as both,
            // so switching follow() to public doesn't strip previously-private groups.
            val list = SimpleGroupListEvent.create(publicGroups = listOf(groupA), privateGroups = listOf(groupB), signer = signer)
            assertEquals(setOf("aaaa", "bbbb"), keysIn(list))
        }

    @Test
    fun followedPrivateGroupRoundTripsThroughDecryption() =
        runBlocking {
            val list = SimpleGroupListEvent.create(privateGroups = listOf(groupA), signer = signer)

            // The group is NOT a public tag — it lives encrypted in content.
            assertTrue(list.publicGroups().isEmpty())
            assertEquals(setOf("aaaa"), keysIn(list))
        }

    @Test
    fun addingASecondGroupKeepsBoth() =
        runBlocking {
            val first = SimpleGroupListEvent.create(privateGroups = listOf(groupA), signer = signer)
            val second = SimpleGroupListEvent.add(first, groupB, isPrivate = true, signer = signer)
            assertEquals(setOf("aaaa", "bbbb"), keysIn(second))
        }

    @Test
    fun unfollowRemovesOnlyThatGroup() =
        runBlocking {
            val both =
                SimpleGroupListEvent.add(
                    SimpleGroupListEvent.create(privateGroups = listOf(groupA), signer = signer),
                    groupB,
                    isPrivate = true,
                    signer = signer,
                )
            val afterRemove = SimpleGroupListEvent.remove(both, groupA, signer = signer)
            val keys = keysIn(afterRemove)
            assertFalse("aaaa" in keys)
            assertTrue("bbbb" in keys)
        }
}
