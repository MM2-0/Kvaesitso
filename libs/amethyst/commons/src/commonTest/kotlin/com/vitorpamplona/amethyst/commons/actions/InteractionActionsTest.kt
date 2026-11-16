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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InteractionActionsTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private suspend fun article(): LongFormContentEvent =
        alice.sign(
            1_700_000_000,
            LongFormContentEvent.KIND,
            arrayOf(arrayOf("d", "my-article"), arrayOf("title", "Title")),
            "Long body",
        )

    @Test
    fun replyToKind1IsANip10Note() =
        runTest {
            val parent = alice.sign(TextNoteEvent.build("hello"))
            val reply = ReplyActions.reply(EventHintBundle(parent, relay), "hi", bob)
            assertIs<TextNoteEvent>(reply)
            assertEquals(parent.id, reply.replyingTo())
        }

    @Test
    fun replyToAnythingElseIsANip22Comment() =
        runTest {
            val target = article()
            val comment = ReplyActions.reply(EventHintBundle(target, relay), "nice read", bob)
            assertIs<CommentEvent>(comment)
            assertEquals(listOf(target.id), comment.rootEventIds())
            assertEquals(listOf(target.addressTag()), comment.rootAddressIds())
            assertTrue(comment.replyAuthorKeys().contains(alice.pubKey), "the parent author must be p-tagged")

            // A comment on that comment keeps the article as its root scope.
            val nested = ReplyActions.reply(EventHintBundle(comment, relay), "agreed", alice)
            assertIs<CommentEvent>(nested)
            assertEquals(listOf(target.id), nested.rootEventIds())
            assertEquals(comment.id, nested.replyingTo())
        }

    @Test
    fun topLevelAmethystNoteGetsACommentLikeTheApp() =
        runTest {
            val amethystRoot = alice.sign<TextNoteEvent>(1_700_000_000, TextNoteEvent.KIND, arrayOf(arrayOf("client", "Amethyst")), "from the app")
            assertTrue(ReplyActions.repliesAsComment(amethystRoot))
            assertIs<CommentEvent>(ReplyActions.reply(EventHintBundle(amethystRoot, relay), "hi", bob))

            // A reply further down an Amethyst thread is still NIP-10, client tag or not.
            val reply =
                alice.sign(
                    TextNoteEvent.build("x", replyingTo = EventHintBundle(alice.sign(TextNoteEvent.build("plain")))) {
                        add(arrayOf("client", "Amethyst"))
                    },
                )
            assertTrue(!ReplyActions.repliesAsComment(reply))
            assertIs<TextNoteEvent>(ReplyActions.reply(EventHintBundle(reply, relay), "y", bob))
        }

    @Test
    fun commentOnKind1IsRefused() =
        runTest {
            val parent = alice.sign(TextNoteEvent.build("hello"))
            assertFailsWith<IllegalArgumentException> {
                ReplyActions.commentOn(EventHintBundle<Event>(parent), "x", bob)
            }
        }

    @Test
    fun replyCarriesMentionsOnceAndKeepsTheGroup() =
        runTest {
            val parent = alice.sign(TextNoteEvent.build("in the group") { add(arrayOf("h", "group1")) })
            val carol = PTag("0".repeat(63) + "9", relay)
            val reply = ReplyActions.replyTo(EventHintBundle(parent), "hi", bob, listOf(carol, carol, PTag(alice.pubKey)))
            val pTags = reply.tags.mapNotNull(PTag::parseKey)
            assertEquals(1, pTags.count { it == carol.pubKey }, "a mention is tagged once")
            assertEquals(1, pTags.count { it == alice.pubKey }, "a mention already in the chain is not repeated")
            assertTrue(reply.tags.any { it[0] == "h" && it[1] == "group1" }, "the reply stays in the NIP-29 group")
        }

    @Test
    fun replyTagsInlineMentions() =
        runTest {
            val parent = alice.sign(TextNoteEvent.build("hello"))
            val carol = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))
            val reply = ReplyActions.reply(EventHintBundle(parent), "cc nostr:${NPub.create(carol.pubKey)}", bob)
            assertTrue(reply.tags.mapNotNull(PTag::parseKey).contains(carol.pubKey))
        }

    @Test
    fun deletionCoversEveryTargetAndRefusesOthersEvents() =
        runTest {
            val mine = (1..3).map { alice.sign(TextNoteEvent.build("note $it")) }
            val deletions = DeletionActions.delete(mine + article(), alice)
            assertEquals(1, deletions.size)
            val deletion: DeletionRequestEvent = deletions.single()
            assertEquals(4, deletion.deleteEventIds().size)
            assertEquals(1, deletion.deleteAddressIds().size)

            val theirs = bob.sign(TextNoteEvent.build("not yours"))
            assertFailsWith<IllegalArgumentException> { DeletionActions.delete(listOf(theirs), alice) }
        }

    @Test
    fun deletionChunksLargeBatches() =
        runTest {
            val many = (1..(DeletionActions.MAX_TARGETS_PER_EVENT + 5)).map { alice.sign(TextNoteEvent.build("n$it")) }
            val deletions = DeletionActions.delete(many, alice)
            assertEquals(2, deletions.size)
            assertEquals(many.size, deletions.sumOf { it.deleteEventIds().size })
        }
}
