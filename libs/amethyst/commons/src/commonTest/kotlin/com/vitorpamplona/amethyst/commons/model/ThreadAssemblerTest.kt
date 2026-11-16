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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Thread anchoring rules: replies and comments (kind 1 / 1111) load the full
 * parent thread; reactions and zaps anchor their own thread — the event is the
 * root and only its reply subtree is included, not the conversation of the
 * post they target.
 */
class ThreadAssemblerTest {
    private val threadRootId = "9".repeat(64)
    private val originalId = "a".repeat(64)
    private val zapId = "b".repeat(64)
    private val reactionId = "c".repeat(64)
    private val replyToZapId = "d".repeat(64)
    private val replyToReactionId = "8".repeat(64)
    private val authorKey = "e".repeat(64)
    private val sig = "f".repeat(128)

    // Thread C: root <- original. `original` is a reply nested inside a thread,
    // standing in for "a reply on another thread" that gets liked/zapped.
    private val threadRoot =
        Note(threadRootId).apply {
            event = TextNoteEvent(threadRootId, authorKey, 999, arrayOf(arrayOf("t", "test")), "thread root", sig)
            replyTo = emptyList()
        }

    private val original =
        Note(originalId).apply {
            event =
                TextNoteEvent(
                    originalId,
                    authorKey,
                    1000,
                    arrayOf(arrayOf("e", threadRootId, "", "root")),
                    "the zapped post",
                    sig,
                )
            replyTo = listOf(threadRoot)
        }

    private val zap =
        Note(zapId).apply {
            event = ZapReceiptEvent(zapId, authorKey, 1001, arrayOf(arrayOf("e", originalId), arrayOf("p", authorKey)), "", sig)
            replyTo = listOf(original)
        }

    private val reaction =
        Note(reactionId).apply {
            event = ReactionEvent(reactionId, authorKey, 1001, arrayOf(arrayOf("e", originalId), arrayOf("p", authorKey)), "+", sig)
            replyTo = listOf(original)
        }

    private val replyToZap =
        Note(replyToZapId).apply {
            event = CommentEvent(replyToZapId, authorKey, 1002, arrayOf(arrayOf("E", zapId), arrayOf("e", zapId)), "nice zap!", sig)
            replyTo = listOf(zap)
        }

    private val replyToReaction =
        Note(replyToReactionId).apply {
            event = CommentEvent(replyToReactionId, authorKey, 1002, arrayOf(arrayOf("E", reactionId), arrayOf("e", reactionId)), "lol same", sig)
            replyTo = listOf(reaction)
        }

    private val cache =
        StubCache(
            mapOf(
                threadRootId to threadRoot,
                originalId to original,
                zapId to zap,
                reactionId to reaction,
                replyToZapId to replyToZap,
                replyToReactionId to replyToReaction,
            ),
        )

    init {
        // Mirrors LocalCache: replies link bidirectionally, but zaps and
        // reactions are credited via addZap/addReaction, not addReply, so the
        // original note's `replies` does not contain them.
        threadRoot.addReply(original)
        zap.addReply(replyToZap)
        reaction.addReply(replyToReaction)
    }

    @Test
    fun zapAnchorsItsOwnThread() {
        val info = ThreadAssembler(cache).findThreadFor(zapId)!!

        assertEquals(zap, info.root)
        assertTrue(info.allNotes.contains(zap))
        assertTrue(info.allNotes.contains(replyToZap), "the zap's replies belong to its thread")
        assertFalse(info.allNotes.contains(original), "the zapped post's thread must not be loaded")
    }

    @Test
    fun reactionAnchorsItsOwnThread() {
        val info = ThreadAssembler(cache).findThreadFor(reactionId)!!

        assertEquals(reaction, info.root)
        assertTrue(info.allNotes.contains(reaction))
        assertFalse(info.allNotes.contains(original), "the liked post's thread must not be loaded")
    }

    @Test
    fun commentReplyingToAZapAnchorsAtTheZapNotTheZappedThread() {
        val info = ThreadAssembler(cache).findThreadFor(replyToZapId)!!

        assertTrue(info.allNotes.contains(replyToZap))
        assertTrue(info.allNotes.contains(zap), "the zap is the root of this thread")
        assertFalse(info.allNotes.contains(original), "the zapped post's conversation must not load")
        assertFalse(info.allNotes.contains(threadRoot), "...nor its thread root")
    }

    @Test
    fun commentReplyingToALikeAnchorsAtTheLikeNotTheLikedThread() {
        // The user's scenario: reply A to like L, where L liked a reply (original)
        // nested in thread C. Opening A's thread must show {L, A}, never thread C.
        val info = ThreadAssembler(cache).findThreadFor(replyToReactionId)!!

        assertTrue(info.allNotes.contains(replyToReaction))
        assertTrue(info.allNotes.contains(reaction), "the like is the root of this thread")
        assertFalse(info.allNotes.contains(original), "the liked post's conversation must not load")
        assertFalse(info.allNotes.contains(threadRoot), "...nor its thread root")
    }

    @Test
    fun replyLevelStopsAtTheReactionBoundary() {
        // Without the boundary the level would climb reaction -> original -> root,
        // burying the reply several levels deep; it must be a direct child of the like.
        assertEquals(0, ThreadLevelCalculator.replyLevel(reaction))
        assertEquals(1, ThreadLevelCalculator.replyLevel(replyToReaction))
        assertEquals(0, ThreadLevelCalculator.replyLevel(zap))
        assertEquals(1, ThreadLevelCalculator.replyLevel(replyToZap))
    }

    private class StubCache(
        private val notesById: Map<HexKey, Note>,
    ) : ICacheProvider {
        override fun getAnyChannel(note: Note): Channel? = null

        override val relayHints = HintIndexer()

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = notesById[hexKey]

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = notesById[hexKey]

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = error("not used by ThreadAssembler in this test")

        override fun getEventStream(): ICacheEventStream = error("not used by ThreadAssembler in this test")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }
}
