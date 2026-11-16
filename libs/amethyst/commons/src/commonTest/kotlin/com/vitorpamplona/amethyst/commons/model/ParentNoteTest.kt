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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParentNoteTest {
    private val communityOwner = "9ca0bd7450742d6a20319c0e3d4c679c9e046a9dc70e8ef55c2905e24052340b"
    private val definitionEventId = "575117c37d66a698ddd81169f88fcdab5d5d63687f79da0c5c65f1d72cb99a57"
    private val moviesAddress = Address(34550, communityOwner, "movies")
    private val movies = moviesAddress.toValue()

    private fun comment(vararg tags: Array<String>) =
        CommentEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1_700_000_000L,
            tags = arrayOf(*tags),
            content = "Ciao a tutti",
            sig = "22".repeat(64),
        )

    private fun noteOf(event: Event) = Note(event.id).apply { this.event = event }

    /**
     * The reported bug. A mostr-bridged top-level post in the "movies" community, viewed before
     * the community definition has been fetched: the cache hands back an empty AddressableNote,
     * which the old `event?.kind != KIND` test accepted (null != 34550) and rendered as a blank
     * "replying to" card.
     */
    @Test
    fun topLevelCommunityPostHasNoParentWhenTheDefinitionIsNotCachedYet() {
        val event =
            comment(
                arrayOf("A", movies, "wss://relay.mostr.pub/"),
                arrayOf("a", movies, "wss://relay.mostr.pub/"),
                arrayOf("E", definitionEventId, "wss://relay.mostr.pub/", communityOwner),
                arrayOf("K", "34550"),
                arrayOf("e", definitionEventId, "wss://relay.mostr.pub/", communityOwner),
                arrayOf("k", "34550"),
            )

        // An AddressableNote that exists in the cache but whose definition event has not arrived.
        val unloadedCommunity = AddressableNote(moviesAddress)
        val cache = StubCache(notesById = mapOf(movies to unloadedCommunity))

        assertNull(replyingDirectlyTo(noteOf(event), cache))
    }

    /**
     * A reply whose own parent isn't tagged: `replyingToAddressOrEvent()` falls back to the root
     * `A`, so the resolved candidate is the community. This is the path the top-level guard does
     * *not* cover -- it reaches the address-kind exclusion, which has to hold even though the
     * community's definition event has never loaded and its `event` is therefore null.
     */
    @Test
    fun replyFallingBackToTheCommunityRootHasNoParent() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
                arrayOf("k", "1111"),
            )

        val unloadedCommunity = AddressableNote(moviesAddress)
        val cache = StubCache(notesById = mapOf(movies to unloadedCommunity))

        assertNull(unloadedCommunity.event, "the exclusion must not depend on the definition being cached")
        assertNull(replyingDirectlyTo(noteOf(event), cache))
    }

    /**
     * The bridged shape that points at the definition by *id*. That id can never resolve, because
     * addressable events are cached by address -- so it must not be offered as a parent either.
     */
    @Test
    fun bridgedCommunityPostPointingAtTheDefinitionByIdHasNoParent() {
        val event =
            comment(
                arrayOf("E", definitionEventId, "", communityOwner),
                arrayOf("K", "34550"),
                arrayOf("e", definitionEventId, "", communityOwner),
                arrayOf("k", "34550"),
            )

        val note = noteOf(event)
        // What LocalCache builds for an `e` tag: a plain Note keyed by id that stays empty.
        val neverLoads = Note(definitionEventId)
        note.replyTo = listOf(neverLoads)
        val cache = StubCache(notesById = mapOf(definitionEventId to neverLoads))

        assertNull(replyingDirectlyTo(note, cache))
    }

    /**
     * A reply to a post *inside* a community keeps `K` = 34550 but points `k` at the parent's
     * kind. The real parent must still be resolved -- the fix must not swallow these.
     */
    @Test
    fun nestedReplyInsideACommunityStillResolvesItsParent() {
        val parentId = "33".repeat(32)
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
                arrayOf("e", parentId),
                arrayOf("k", "1111"),
            )

        val parent = Note(parentId)
        val note = noteOf(event)
        note.replyTo = listOf(AddressableNote(moviesAddress), parent)
        val cache = StubCache(notesById = mapOf(parentId to parent, movies to AddressableNote(moviesAddress)))

        assertSame(parent, replyingDirectlyTo(note, cache))
    }

    /**
     * The predicate `RenderRepost` now shares (`note.replyTo?.lastOrNull { !it.isCommunityDefinition() }`).
     * The composable itself needs an instrumented test, but the logic that changed is this
     * predicate: it has to recognise the community from the address, with no event loaded.
     */
    @Test
    fun communityIsRecognisedWithoutItsDefinitionEvent() {
        val uncached = AddressableNote(moviesAddress)
        assertNull(uncached.event, "precondition: the definition has not arrived")
        assertTrue(uncached.isCommunityDefinition(), "an uncached community must still be excluded")

        val article = AddressableNote(Address(30023, communityOwner, "some-article"))
        assertFalse(article.isCommunityDefinition(), "other addressable kinds must not be excluded")

        val plainNote = Note("55".repeat(32))
        assertFalse(plainNote.isCommunityDefinition(), "a note with no event and no address is not a community")
    }

    /** The selection `RenderRepost` performs: skip the community, take the real boosted note. */
    @Test
    fun repostSelectionSkipsAnUncachedCommunityAndTakesTheRealNote() {
        val boosted = Note("66".repeat(32))
        val replyTo = listOf(AddressableNote(moviesAddress), boosted)

        assertSame(boosted, replyTo.lastOrNull { !it.isCommunityDefinition() })

        // Community-only: nothing to render, rather than the empty shell that produced the blank.
        assertNull(listOf(AddressableNote(moviesAddress)).lastOrNull { !it.isCommunityDefinition() })
    }

    /** A post in a channel is rendered by the channel header, not as a parent note. */
    @Test
    fun noteInsideAChannelIsNotOfferedAsAParent() {
        val parentId = "44".repeat(32)
        val event = comment(arrayOf("e", parentId), arrayOf("k", "1111"))
        val parent = Note(parentId)
        val note = noteOf(event)
        val cache = StubCache(notesById = mapOf(parentId to parent), channelFor = setOf(parentId))

        assertNull(replyingDirectlyTo(note, cache))
    }
}
