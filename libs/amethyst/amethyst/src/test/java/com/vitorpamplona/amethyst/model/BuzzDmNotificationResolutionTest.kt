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
package com.vitorpamplona.amethyst.model

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The resolution the Notification feed relies on to surface a Buzz DM: given a bare kind-40002 message
 * [Note], [LocalCache.getRelayGroupChannelForContent] finds the group channel it belongs to, and that
 * channel's kind-39000 metadata answers `isBuzzDm()` + `buzzParticipants()`. This is what
 * `NotificationFeedFilter.isBuzzDmForMe` composes; the per-kind acceptance is exercised at the filter.
 */
class BuzzDmNotificationResolutionTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val me = NostrSignerInternal(KeyPair())
    private val other = NostrSignerInternal(KeyPair())

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper (null == null reads
        // as "main"). Distinct mocks make it a worker thread. See BuzzWorkspaceChannelTest.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        BuzzRelayDialect.clearForTesting()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        BuzzRelayDialect.clearForTesting()
    }

    private suspend fun dmMetadata(
        channelId: String,
        participants: List<String>,
    ) = me.sign(
        GroupMetadataEvent.build(channelId, name = "DM") {
            add(arrayOf("t", "dm"))
            participants.forEach { add(arrayOf("p", it)) }
        },
    )

    // Attach a 40002 by consuming it (which materializes the group channel) and then set its 39000
    // metadata directly — the real 39000 consume gates on LocalCache.appHost.nip11Cache, which isn't
    // available in a plain JVM test (see BuzzWorkspaceChannelTest). We still drive the real resolver.
    private fun consumeMessageInto(
        channelId: String,
        metadata: GroupMetadataEvent,
        message: Event,
    ): Note {
        LocalCache.checkDeletionAndConsume(message, buzzRelay, false)
        LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))!!.event = metadata
        return LocalCache.getNoteIfExists(message.id)!!
    }

    @Test
    fun `a 40002 in a dm channel resolves to its channel whose metadata says dm and lists me`() =
        runBlocking {
            val channelId = UUID.randomUUID().toString()
            val meHex = me.pubKey
            val otherHex = other.pubKey

            val message = other.sign(StreamMessageV2Event.build(channelId, "hey"))
            val note = consumeMessageInto(channelId, dmMetadata(channelId, listOf(meHex, otherHex)), message)

            val metadata = LocalCache.getRelayGroupChannelForContent(note)?.event
            assertNotNull("the message resolves back to its DM channel", metadata)
            assertTrue("the channel metadata is marked t=dm", metadata!!.isBuzzDm())
            assertTrue("I am one of the DM participants", metadata.buzzParticipants().contains(meHex))
            assertEquals(setOf(meHex, otherHex), metadata.buzzParticipants().toSet())
        }

    @Test
    fun `a kind-9 chat message in a dm channel also resolves to its dm channel`() =
        runBlocking {
            // The deployed Buzz relay carries DM messages as kind-9 too, not only 40002; the resolver is
            // kind-agnostic (keys off the h tag) so both surface the same DM channel.
            val channelId = UUID.randomUUID().toString()
            val meHex = me.pubKey
            val chat = other.sign(ChatEvent.build("hey") { add(GroupIdTag.assemble(channelId)) })

            val note = consumeMessageInto(channelId, dmMetadata(channelId, listOf(meHex, other.pubKey)), chat)

            val metadata = LocalCache.getRelayGroupChannelForContent(note)?.event
            assertNotNull("the kind-9 message resolves back to its DM channel", metadata)
            assertTrue(metadata!!.isBuzzDm())
        }

    @Test
    fun `a 40002 in a non-dm channel resolves to a channel that is not a dm`() =
        runBlocking {
            val channelId = UUID.randomUUID().toString()
            // A plain (non-dm) channel: 39000 without the t=dm marker.
            val message = other.sign(StreamMessageV2Event.build(channelId, "gm"))
            val note = consumeMessageInto(channelId, me.sign(GroupMetadataEvent.build(channelId, name = "general")), message)

            val metadata = LocalCache.getRelayGroupChannelForContent(note)?.event
            assertNotNull(metadata)
            assertFalse("a general channel is not a DM", metadata!!.isBuzzDm())
        }
}
