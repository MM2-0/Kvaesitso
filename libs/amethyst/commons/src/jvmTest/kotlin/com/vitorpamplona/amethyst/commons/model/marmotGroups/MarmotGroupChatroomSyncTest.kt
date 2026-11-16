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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.InMemoryIngestDedupStore
import com.vitorpamplona.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotPublisher
import com.vitorpamplona.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [syncMetadataTo] is the one place the library's group state reaches the chatroom the UI
 * reads. The library tests the state itself; these test that it lands on the chatroom.
 */
class MarmotGroupChatroomSyncTest {
    private val nostrGroupId = "d".repeat(64)
    private val acceptingRelay = MarmotPublisher { _, _ -> true }

    private suspend fun MarmotManager.createGroup() =
        createCurrentProfileGroup(
            nostrGroupId = nostrGroupId,
            relays = listOf("wss://relay.invalid"),
            profile = GroupProfileV1("ours", ""),
        )

    @Test
    fun `the gate reaches the chatroom`() =
        runBlocking {
            // The UI cannot ask a suspending publish gate from the synchronous
            // path that refreshes a conversation, so the gate is mirrored onto
            // the chatroom. If that mirror is missing, the composer stays
            // enabled on a group that refuses every send and the user finds out
            // by tapping.
            val manager = MarmotManager(NostrSignerInternal(KeyPair()), InMemoryMlsGroupStateStore(), publisher = acceptingRelay)
            manager.createGroup()
            val chatroom = MarmotGroupChatroom(nostrGroupId)

            manager.syncMetadataTo(nostrGroupId, chatroom)
            assertNull(chatroom.outboundGate.value, "a live group has no gate")
            assertEquals("ours", chatroom.displayName.value)

            manager.disbandGroup(nostrGroupId)
            manager.syncMetadataTo(nostrGroupId, chatroom)
            assertEquals(LocalOutboundGate.DISBANDING, chatroom.outboundGate.value)
        }

    @Test
    fun `a group we created is Known after a restart`() =
        runBlocking {
            // "Known" at creation was an in-memory flag, so after a restart a creator who
            // had not posted yet found their own group under New Requests. The sync that
            // runs on restore has to reach the same answer from the stored state alone.
            val signer = NostrSignerInternal(KeyPair())
            val states = InMemoryMlsGroupStateStore()
            val markers = InMemoryIngestDedupStore()
            MarmotManager(signer, states, publisher = acceptingRelay, ingestDedupStore = markers).createGroup()

            val after = MarmotManager(signer, states, publisher = acceptingRelay, ingestDedupStore = markers)
            after.restoreAll()
            val room = MarmotGroupChatroom(nostrGroupId)
            after.syncMetadataTo(nostrGroupId, room)

            assertTrue(room.isKnown(emptySet()), "the creator's own group, after a restart")
        }
}
