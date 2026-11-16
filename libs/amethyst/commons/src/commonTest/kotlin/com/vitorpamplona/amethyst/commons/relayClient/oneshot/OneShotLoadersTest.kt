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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneShotLoadersTest {
    private fun signer(n: Int) = NostrSignerInternal(KeyPair(n.toString(16).padStart(64, '0').hexToByteArray()))

    private val alice = signer(7)
    private val bob = signer(8)
    private val carol = signer(9)

    private val aliceIn = relay("alice-in")
    private val aliceOut = relay("alice-out")

    private suspend fun relayList(
        who: NostrSignerInternal,
        read: NormalizedRelayUrl,
        write: NormalizedRelayUrl,
    ) = AdvertisedRelayListEvent.create(listOf(AdvertisedRelayInfo(read, AdvertisedRelayType.READ), AdvertisedRelayInfo(write, AdvertisedRelayType.WRITE)), who)

    @Test
    fun parsesEveryEventSpelling() {
        val id = "ab47ce8e660e1bcd5a31bc0b6ec31fa20337bc2288c935a03af62f0b66b36c22"
        val author = "ec33d3644b9010db51edef5e62261c9cb9c8664d1696564f5a8c897ad9e74025"
        val hint = relay("hint")
        assertEquals(listOf(id), EventRef.parse(id).filter.ids)
        assertEquals(listOf(id), EventRef.parse(id.uppercase()).filter.ids)
        assertEquals(listOf(id), EventRef.parse(NNote.create(id)).filter.ids)

        val nevent = EventRef.parse("nostr:" + NEvent.create(id, author, 1, hint))
        assertEquals(listOf(id), nevent.filter.ids)
        assertEquals(author, nevent.author)
        assertEquals(setOf(hint), nevent.hints)

        val naddr = EventRef.parse(NAddress.create(30023, author, "slug", hint))
        assertEquals(listOf(30023), naddr.filter.kinds)
        assertEquals(listOf(author), naddr.filter.authors)
        assertEquals(mapOf("d" to listOf("slug")), naddr.filter.tags)

        assertFailsWith<IllegalArgumentException> { EventRef.parse("nope") }
        assertFailsWith<IllegalArgumentException> { EventRef.parse("npub1aseaxeztjqgdk50daa0xyfsunjuusejdz6t9vn663jyh4k08gqjsnft2g2") }
    }

    @Test
    fun locateAnswersFromTheStoreWithoutAskingRelays() =
        runTest {
            val access = FakeRelayAccess()
            val note = alice.sign(TextNoteEvent.build("hi"))
            access.storeAll(note)

            val located = assertNotNull(EventLocator.locate(access, EventRef.parse(note.id), refresh = false, timeoutMs = 1))
            assertEquals("cache", located.source)
            assertTrue(access.requests.isEmpty())
        }

    @Test
    fun locateFollowsTheAuthorsOutboxFoundOnTheBootstrapSet() =
        runTest {
            val access = FakeRelayAccess()
            val note = alice.sign(TextNoteEvent.build("hi"))
            // Alice's relay list is only on the bootstrap relay; the note only on her outbox.
            access.publish(relay("bootstrap"), relayList(alice, aliceIn, aliceOut))
            access.publish(aliceOut, note)

            val located = assertNotNull(EventLocator.locate(access, EventRef.parse(NEvent.create(note.id, alice.pubKey, 1, null)), refresh = false, timeoutMs = 1))
            assertEquals("relays", located.source)
            assertEquals(note.id, located.event.id)
            assertEquals(setOf(aliceOut), located.seenOn)
        }

    @Test
    fun profilesPreferTheNewestAndOnlyFetchWhenAsked() =
        runTest {
            val access = FakeRelayAccess()
            access.storeAll(
                alice.sign(MetadataEvent.createNew(name = "old", createdAt = 100)),
                alice.sign(MetadataEvent.createNew(name = "new", createdAt = 200)),
            )
            access.publish(relay("index"), bob.sign(MetadataEvent.createNew(name = "bob")))

            val offline = ProfileLoader.renderContext(access, listOf(alice.pubKey, bob.pubKey), fetchMissing = false, timeoutMs = 1)
            assertEquals("new", offline.profiles[alice.pubKey]?.name)
            assertNull(offline.profiles[bob.pubKey])
            assertTrue(access.requests.isEmpty())

            val online = ProfileLoader.renderContext(access, listOf(alice.pubKey, bob.pubKey), fetchMissing = true, timeoutMs = 1)
            assertEquals("bob", online.profiles[bob.pubKey]?.name)
            // Only the missing author is asked for.
            assertEquals(
                listOf(bob.pubKey),
                access.requests
                    .single()
                    .values
                    .first()
                    .single()
                    .authors,
            )
        }

    @Test
    fun noteCacheAppliesStoredDeletionsAndBatchesMissingRelayLists() =
        runTest {
            val access = FakeRelayAccess()
            val note = alice.sign(TextNoteEvent.build("oops"))
            access.storeAll(alice.sign(DeletionRequestEvent.build(listOf(note))))
            access.publish(relay("bootstrap"), relayList(bob, relay("bob-in"), relay("bob-out")), relayList(carol, relay("carol-in"), relay("carol-out")))

            val notes = OneShotNoteCache(access, timeoutMs = 1)
            notes.addAll(listOf(aliceOut to note))
            assertNull(notes.cache.getNoteIfExists(note.id)?.event, "a relay that missed the kind:5 must not bring the note back")

            notes.addUsers(listOf(bob.pubKey, carol.pubKey), fetchMissing = true)
            assertEquals(1, access.requests.size, "one request for every missing list")
            assertEquals(setOf(relay("bob-in")), notes.user(bob.pubKey).inboxRelays()?.toSet())
            assertEquals(setOf(relay("carol-in")), notes.user(carol.pubKey).inboxRelays()?.toSet())

            // Known now: no second request.
            notes.addUsers(listOf(bob.pubKey), fetchMissing = true)
            assertEquals(1, access.requests.size)
        }

    @Test
    fun threadLoadsParentsAndRepliesAndSkipsDeletedOnes() =
        runTest {
            val access = FakeRelayAccess()
            access.storeAll(relayList(alice, aliceIn, aliceOut))
            val root = alice.sign(TextNoteEvent.build("root"))
            val bobReply = ReplyActions.reply(EventHintBundle(root, aliceOut), "bob replies", bob) as TextNoteEvent
            val carolReply = ReplyActions.reply(EventHintBundle<Event>(bobReply, aliceIn), "carol replies", carol)
            val carolDeleted = ReplyActions.reply(EventHintBundle<Event>(bobReply, aliceIn), "carol regrets", carol)
            access.storeAll(bobReply, carol.sign(DeletionRequestEvent.build(listOf(carolDeleted))))
            access.publish(aliceOut, root)
            // Replies notify the root author: they sit in Alice's inbox.
            access.publish(aliceIn, carolReply, carolDeleted)

            val focus = LocatedEvent(bobReply, emptySet(), "cache")
            val thread = assertNotNull(ThreadLoader(access, timeoutMs = 1).load(focus, viewer = alice.pubKey))

            val shown = thread.ordered.mapNotNull { it.event?.id }
            assertEquals(listOf(root.id, bobReply.id, carolReply.id), shown)
            assertEquals(root.id, thread.root?.event?.id)
            assertEquals(listOf(0, 1, 2), thread.ordered.filter { it.event != null }.map { thread.depth(it) })
            assertEquals(bobReply.id, thread.focus.idHex)
        }

    @Test
    fun threadOfADeletedNoteIsNull() =
        runTest {
            val access = FakeRelayAccess()
            val note = alice.sign(TextNoteEvent.build("gone"))
            access.storeAll(note, alice.sign(DeletionRequestEvent.build(listOf(note))))
            assertNull(ThreadLoader(access, timeoutMs = 1).load(LocatedEvent(note, emptySet(), "cache"), viewer = alice.pubKey))
        }

    @Test
    fun notificationsCountOnlyInteractionsWithOurOwnNotes() =
        runTest {
            val access = FakeRelayAccess()
            val inbox = setOf(aliceIn)
            val outbox = setOf(aliceOut)
            val stored = alice.sign(TextNoteEvent.build("in the store"))
            val remote = alice.sign(TextNoteEvent.build("only on my outbox"))
            val carolNote = carol.sign(TextNoteEvent.build("carol's"))
            access.storeAll(stored, carolNote)
            access.publish(aliceOut, remote)

            val likeStored = bob.sign(ReactionEvent.build("+", EventHintBundle<Event>(stored)))
            val likeRemote = bob.sign(ReactionEvent.build("+", EventHintBundle<Event>(remote)))
            // A reaction to Carol's note that also p-tags Alice is not Alice's notification.
            val likeCarol = bob.sign(ReactionEvent.build("+", EventHintBundle<Event>(carolNote)) { pTag(alice.pubKey, null) })
            val mention = carol.sign(TextNoteEvent.build("hey") { pTag(alice.pubKey, null) })
            access.publish(aliceIn, likeStored, likeRemote, likeCarol, mention)

            val all = NotificationsLoader.load(access, alice.pubKey, inbox, outbox, limit = 10, timeoutMs = 1)
            assertEquals(setOf(likeStored.id, likeRemote.id, mention.id), all.map { it.event.id }.toSet())

            val reactions = NotificationsLoader.load(access, alice.pubKey, inbox, outbox, limit = 10, types = setOf("reaction"), timeoutMs = 1)
            assertTrue(reactions.all { it.type == "reaction" } && reactions.size == 2, reactions.map { it.type }.toString())
        }
}
