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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.buzzEffectiveAuthor
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.isBuzzEditableBy
import com.vitorpamplona.amethyst.commons.model.latestBuzzEdit
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.stream.BuzzChatMessage
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The Buzz dialect of NIP-29 in `LocalCache`: dialect detection off VERIFIED events,
 * timeline attachment into the group's (stable, never-swapped) `RelayGroupChannel`, and
 * the kind-40003 edit overlay anchored on the message it edits (`Note.edits`).
 */
class BuzzWorkspaceChannelTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val signer = NostrSignerInternal(KeyPair())

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        BuzzRelayDialect.clearForTesting()
        BuzzWorkspaceStates.clearForTesting()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        BuzzRelayDialect.clearForTesting()
        BuzzWorkspaceStates.clearForTesting()
    }

    private fun newChannelId() = UUID.randomUUID().toString()

    private suspend fun streamMessage(
        channelId: String,
        text: String,
    ) = signer.sign(StreamMessageV2Event.build(channelId, text))

    @Test
    fun verifiedBuzzKindMarksDialectAndAttachesToTheSameChannel() =
        runBlocking {
            val channelId = newChannelId()
            val msg = streamMessage(channelId, "hello workspace")

            assertFalse(BuzzRelayDialect.isBuzz(buzzRelay))

            LocalCache.checkDeletionAndConsume(msg, buzzRelay, false)

            assertTrue("a verified 40002 marks the serving relay as Buzz", BuzzRelayDialect.isBuzz(buzzRelay))

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))
            assertNotNull("the group channel exists after a 40002 lands", channel)
            // The channel type never changes on dialect discovery — screens capture it for life.
            assertTrue(channel is RelayGroupChannel)
            assertTrue("the message is on the channel timeline", channel!!.notes.containsKey(msg.id))
        }

    @Test
    fun channelInstanceIsStableAcrossDialectDiscovery() =
        runBlocking {
            val channelId = newChannelId()
            val key = GroupId(channelId, buzzRelay)

            // The group materializes plain (e.g. via 39000) BEFORE any Buzz kind reveals the dialect.
            val before = LocalCache.getOrCreateRelayGroupChannel(key)

            LocalCache.checkDeletionAndConsume(streamMessage(channelId, "first buzz kind"), buzzRelay, false)

            val after = LocalCache.getRelayGroupChannelIfExists(key)
            // Same object: no swap, so every live screen/feed/composer reference stays valid.
            assertTrue("dialect discovery must NOT swap the channel instance", before === after)
        }

    @Test
    fun unverifiedEventDoesNotMarkTheDialect() =
        runBlocking {
            val channelId = newChannelId()
            // Tamper the signature so verification fails.
            val real = streamMessage(channelId, "spoof")
            val forged = StreamMessageV2Event(real.id, real.pubKey, real.createdAt, real.tags, real.content, "00".repeat(64))

            LocalCache.checkDeletionAndConsume(forged, buzzRelay, false)

            assertFalse("an unverifiable event must not flip the dialect", BuzzRelayDialect.isBuzz(buzzRelay))
        }

    @Test
    fun editOverlayTracksNewestEditAndIsNotATimelineRow() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "teh typo")
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)

            val edit1 =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "the typo", createdAt = original.createdAt + 10),
                )
            val edit2 =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "the fix", createdAt = original.createdAt + 20),
                )

            // Out-of-order arrival: newer first, older second.
            LocalCache.checkDeletionAndConsume(edit2, buzzRelay, false)
            LocalCache.checkDeletionAndConsume(edit1, buzzRelay, false)

            // The edits are anchored on the message they edit (Note.edits); newest by created_at wins.
            val target = LocalCache.getNoteIfExists(original.id)!!
            val newest = target.edits.filter { it.event is StreamMessageEditEvent }.maxByOrNull { it.createdAt() ?: 0L }
            assertEquals("newest edit wins regardless of arrival order", "the fix", newest?.event?.content)
            assertEquals(edit2.id, newest?.idHex)

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))!!
            assertFalse("edits are overlays, never timeline rows", channel.notes.containsKey(edit1.id))
            assertFalse("edits are overlays, never timeline rows", channel.notes.containsKey(edit2.id))
        }

    @Test
    fun ownSendsWithNoRelayStillOverlayTheirMessage() =
        runBlocking {
            // An edit consumed with a null provenance relay (own optimistic send) must still
            // overlay its message — it is anchored on the message note, independent of any relay.
            val channelId = newChannelId()
            val original = streamMessage(channelId, "original")
            // Taken before consuming and held: with no relay the message joins no channel
            // timeline, so nothing else holds its note in LocalCache's weak store, and a GC
            // before the read-back would drop it along with its edit overlay.
            val target = LocalCache.getOrCreateNote(original.id)
            LocalCache.checkDeletionAndConsume(original, null, true)

            val edit =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "edited offline", createdAt = original.createdAt + 5),
                )
            LocalCache.checkDeletionAndConsume(edit, null, true)

            val newest = target.edits.filter { it.event is StreamMessageEditEvent }.maxByOrNull { it.createdAt() ?: 0L }
            assertEquals("edited offline", newest?.event?.content)
        }

    @Test
    fun aForgedEditByAnotherAuthorNeverOverridesTheMessage() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "the truth") // authored by `signer`
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)

            // Mallory publishes a well-formed, VERIFIED 40003 targeting someone else's message.
            val mallory = NostrSignerInternal(KeyPair())
            val forged =
                mallory.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "lies", createdAt = original.createdAt + 100),
                )
            LocalCache.checkDeletionAndConsume(forged, buzzRelay, false)

            // The forged edit still lands in the store (it is a valid signed event)…
            val target = LocalCache.getNoteIfExists(original.id)!!
            assertTrue("the forged edit is stored", target.edits.any { it.idHex == forged.id })
            // …but the overlay only applies the ORIGINAL author's edits, so it is ignored.
            assertNull(
                "an edit by a different author must never override the message",
                target.latestBuzzEdit(),
            )

            // The real author's own later edit does apply.
            val real = signer.sign(StreamMessageEditEvent.build(channelId, original.id, "the fix", createdAt = original.createdAt + 200))
            LocalCache.checkDeletionAndConsume(real, buzzRelay, false)
            assertEquals("the fix", target.latestBuzzEdit()?.event?.content)
        }

    @Test
    fun anEditOfAKind9MessageOverlaysIt() =
        runBlocking {
            val channelId = newChannelId()
            // Buzz channel messages are kind 9 now; a 40003 targets them the same way.
            LocalCache.checkDeletionAndConsume(streamMessage(channelId, "mark the dialect"), buzzRelay, false)
            val original = signer.sign(BuzzChatMessage.build(channelId, "teh fix"))
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)
            val edit = signer.sign(StreamMessageEditEvent.build(channelId, original.id, "the fix", createdAt = original.createdAt + 1))
            LocalCache.checkDeletionAndConsume(edit, buzzRelay, false)

            assertEquals(
                "the fix",
                LocalCache
                    .getNoteIfExists(original.id)!!
                    .latestBuzzEdit()
                    ?.event
                    ?.content,
            )
        }

    @Test
    fun theVerifiedOwnerOfAnAgentMayEditItsMessages() =
        runBlocking {
            val channelId = newChannelId()
            val agentKeys = KeyPair()
            val ownerKeys = KeyPair()
            val agent = NostrSignerInternal(agentKeys)
            val owner = NostrSignerInternal(ownerKeys)

            val original = agent.sign(StreamMessageV2Event.build(channelId, "agent output"))
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)
            val ownerEdit = owner.sign(StreamMessageEditEvent.build(channelId, original.id, "corrected by owner", createdAt = original.createdAt + 10))
            LocalCache.checkDeletionAndConsume(ownerEdit, buzzRelay, false)
            val target = LocalCache.getNoteIfExists(original.id)!!

            // Until the agent's profile names the owner, the owner is just another pubkey.
            assertNull(target.latestBuzzEdit())
            assertFalse(target.isBuzzEditableBy(owner.pubKey))

            // The agent's kind 0 carries the owner's NIP-OA attestation for the agent key.
            val attestation = OwnerAttestation.sign(agent.pubKey, "", ownerKeys.privKey!!)
            val profile = agent.sign(MetadataEvent.newUser("agent", createdAt = original.createdAt) { add(attestation.toTag()) })
            LocalCache.checkDeletionAndConsume(profile, buzzRelay, false)

            assertTrue(target.isBuzzEditableBy(owner.pubKey))
            assertEquals("corrected by owner", target.latestBuzzEdit()?.event?.content)
        }

    @Test
    fun aRelaySignedMessageIsEditableByTheMemberItNames() =
        runBlocking {
            val channelId = newChannelId()
            val relayKey = NostrSignerInternal(KeyPair())
            val member = NostrSignerInternal(KeyPair())
            val stranger = NostrSignerInternal(KeyPair())

            // The relay-signed 39000 is what tells us the relay's key.
            LocalCache.checkDeletionAndConsume(relayKey.sign(GroupMetadataEvent.build(channelId, name = "ops")), buzzRelay, false)
            LocalCache.checkDeletionAndConsume(streamMessage(channelId, "mark the dialect"), buzzRelay, false)

            // A workflow posted on the member's behalf: relay-signed, attributed through `actor`.
            val posted = relayKey.sign(BuzzChatMessage.build(channelId, "deploy done") { add(arrayOf("actor", member.pubKey)) })
            LocalCache.checkDeletionAndConsume(posted, buzzRelay, false)
            val target = LocalCache.getNoteIfExists(posted.id)!!
            assertEquals(member.pubKey, target.buzzEffectiveAuthor())

            LocalCache.checkDeletionAndConsume(stranger.sign(StreamMessageEditEvent.build(channelId, posted.id, "nope", createdAt = posted.createdAt + 1)), buzzRelay, false)
            assertNull(target.latestBuzzEdit())
            LocalCache.checkDeletionAndConsume(member.sign(StreamMessageEditEvent.build(channelId, posted.id, "deploy done (v2)", createdAt = posted.createdAt + 2)), buzzRelay, false)
            assertEquals("deploy done (v2)", target.latestBuzzEdit()?.event?.content)

            // A user-signed message can't hand its edits to someone else with an `actor` tag.
            val claimed = stranger.sign(BuzzChatMessage.build(channelId, "mine") { add(arrayOf("actor", member.pubKey)) })
            LocalCache.checkDeletionAndConsume(claimed, buzzRelay, false)
            assertEquals(stranger.pubKey, LocalCache.getNoteIfExists(claimed.id)!!.buzzEffectiveAuthor())
        }

    @Test
    fun deletingAnEditUnlinksItFromTheMessage() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "typo")
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)
            val edit = signer.sign(StreamMessageEditEvent.build(channelId, original.id, "fixed", createdAt = original.createdAt + 5))
            LocalCache.checkDeletionAndConsume(edit, buzzRelay, false)

            val target = LocalCache.getNoteIfExists(original.id)!!
            assertEquals("fixed", target.latestBuzzEdit()?.event?.content)

            // The author deletes their own edit (NIP-09). It must stop overlaying the message and
            // be unlinked from Note.edits, not linger as a stale overlay.
            val deletion = signer.sign(DeletionRequestEvent.build(listOf(edit)))
            LocalCache.checkDeletionAndConsume(deletion, buzzRelay, false)

            assertNull("a deleted edit must no longer overlay its message", target.latestBuzzEdit())
            assertTrue("the deleted edit is unlinked from the message", target.edits.none { it.idHex == edit.id })
        }

    @Test
    fun pruningAMessageReleasesItsEdits() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "will be pruned")
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)
            val edit = signer.sign(StreamMessageEditEvent.build(channelId, original.id, "edit", createdAt = original.createdAt + 5))
            LocalCache.checkDeletionAndConsume(edit, buzzRelay, false)

            val target = LocalCache.getNoteIfExists(original.id)!!
            assertTrue("the edit is anchored on its message", target.edits.any { it.idHex == edit.id })

            // An edit lives in Note.edits, so reaping the message releases the overlay with it —
            // no separate side store to prune.
            target.clearChildLinks()
            assertTrue("overlay for a pruned message must be dropped", target.edits.isEmpty())
        }
}
