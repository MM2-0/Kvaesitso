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

import com.vitorpamplona.amethyst.commons.model.ThreadLevelCalculator
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.viewmodels.thread.ThreadFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regressions for the second amy audit. */
class OneShotAuditTest {
    private fun signer(n: Int) = NostrSignerInternal(KeyPair(n.toString(16).padStart(64, '0').hexToByteArray()))

    private val alice = signer(7)
    private val bob = signer(8)

    private suspend fun article(
        dTag: String,
        createdAt: Long,
        vararg aTags: String,
    ): LongFormContentEvent = alice.sign(createdAt, LongFormContentEvent.KIND, arrayOf(arrayOf("d", dTag)) + aTags.map { arrayOf("a", it) }, "body $dTag")

    @Test
    fun articlesThatCiteEachOtherDoNotOverflowTheThreadLayout() =
        runTest {
            val cache = EventCache()
            val a = article("a", 100, "30023:${alice.pubKey}:b")
            val b = article("b", 200, "30023:${alice.pubKey}:a")
            cache.justConsume(a, null, true)
            cache.justConsume(b, null, true)
            val noteA = cache.getAddressableNoteIfExists(a.address())!!

            val ordered = ThreadFeedFilter.thread(noteA.idHex, cache, cache.getOrCreateUser(alice.pubKey), emptySet())
            assertTrue(ordered.isNotEmpty())
            ordered.forEach { ThreadLevelCalculator.replyLevel(it) }
        }

    @Test
    fun aDeletedArticleVersionDoesNotComeBack() =
        runTest {
            val access = FakeRelayAccess()
            val v1 = article("post", 100)
            val v2 = article("post", 200)
            // The author deletes the article by address (and the version they had).
            access.storeAll(alice.sign(DeletionRequestEvent.build(listOf(v2), createdAt = 300)))

            val notes = OneShotNoteCache(access)
            notes.addAll(listOf(relay("stale") to v1))
            assertNull(notes.cache.getAddressableNoteIfExists(v1.address())?.event, "v1 predates the address deletion")
        }

    @Test
    fun notificationsDropRepliesDeletedInTheStore() =
        runTest {
            val access = FakeRelayAccess()
            val mine = alice.sign(TextNoteEvent.build("mine"))
            access.storeAll(mine)
            val reply = bob.sign(TextNoteEvent.build("regret") { pTag(alice.pubKey, null) })
            access.storeAll(bob.sign(DeletionRequestEvent.build(listOf(reply))))
            access.publish(relay("alice-in"), reply)

            val items = NotificationsLoader.load(access, alice.pubKey, setOf(relay("alice-in")), emptySet(), limit = 10, timeoutMs = 1)
            assertTrue(items.isEmpty(), items.map { it.event.content }.toString())
        }

    @Test
    fun locateIgnoresEventsDeletedInTheStore() =
        runTest {
            val access = FakeRelayAccess()
            val note = bob.sign(TextNoteEvent.build("gone"))
            access.storeAll(bob.sign(DeletionRequestEvent.build(listOf(note))))
            access.publish(relay("bootstrap"), note)
            assertNull(EventLocator.locate(access, EventRef.parse(note.id), refresh = true, timeoutMs = 1))
        }

    @Test
    fun typeFilterAsksRelaysOnlyForThoseKinds() =
        runTest {
            val access = FakeRelayAccess()
            val mine = alice.sign(TextNoteEvent.build("mine"))
            access.storeAll(mine)
            // Many replies newer than the one reaction: a kind-agnostic limit would only see replies.
            val replies = (1..30).map { bob.sign(TextNoteEvent.build("reply $it", createdAt = 1000L + it) { pTag(alice.pubKey, null) }) }
            val like = bob.sign(ReactionEvent.build("+", EventHintBundle<Event>(mine), createdAt = 500))
            access.publish(relay("alice-in"), *(replies + like).toTypedArray<Event>())

            val items = NotificationsLoader.load(access, alice.pubKey, setOf(relay("alice-in")), emptySet(), limit = 5, types = setOf("reaction"), timeoutMs = 1)
            assertEquals(listOf(like.id), items.map { it.event.id })
        }

    @Test
    fun naddrOfAReplaceableKindHasNoDTagFilter() {
        val ref = EventRef.parse(NAddress.create(AdvertisedRelayListEvent.KIND, alice.pubKey, "", null))
        assertNull(ref.filter.tags)
        assertEquals(listOf(AdvertisedRelayListEvent.KIND), ref.filter.kinds)
    }

    @Test
    fun aRelayListIsFetchedOncePerRun() =
        runTest {
            val access = FakeRelayAccess()
            val notes = OneShotNoteCache(access)
            notes.addUsers(listOf(bob.pubKey), fetchMissing = true)
            notes.addUsers(listOf(bob.pubKey), fetchMissing = true)
            assertEquals(1, access.requests.size, "nobody has Bob's list: asking twice cannot help")
        }

    @Test
    fun locateReportsOnlyRelaysThatServedTheEvent() =
        runTest {
            val access = FakeRelayAccess()
            val note = bob.sign(TextNoteEvent.build("hi"))
            access.publish(relay("bootstrap"), note)
            val ref = EventRef.parse(NEvent.create(note.id, null, null, relay("empty-hint")))
            val located = assertNotNull(EventLocator.locate(access, ref, refresh = false, timeoutMs = 1))
            assertEquals(setOf(relay("bootstrap")), located.seenOn)
        }
}
