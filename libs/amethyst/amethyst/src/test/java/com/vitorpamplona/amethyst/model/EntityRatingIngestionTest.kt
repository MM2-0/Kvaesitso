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

import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kind 34259 used to be dropped by `LocalCache`'s "Event Not Supported" fallback, so it never
 * became a `Note` and nothing downstream could ever render it. These tests pin the ingest path
 * and the addressable replacement semantics that come with it.
 *
 * `LocalCache` is a process-wide object and JUnit 4's method order is hash-based, so every test
 * below uses its own author and identifier.
 *
 * Events go in through [consumePinned], which takes each event's note from the cache *before*
 * consuming it and keeps it for the test's lifetime. `LocalCache` holds notes weakly
 * (LargeSoftCache), so a note nothing references can be collected between two consumes — the
 * older rating then lands on a fresh, empty slot — or before the read-back. In the app the
 * screen showing the rating is that strong referent.
 */
class EntityRatingIngestionTest {
    private val publisher = "b1".repeat(32)

    private val pinned = mutableListOf<Note>()

    private fun consumePinned(event: Event): Boolean {
        pinned.add(LocalCache.getOrCreateNote(event))
        return LocalCache.justConsume(event, null, true)
    }

    private fun rating(
        id: String,
        author: String,
        identifier: String,
        stars: Int,
        createdAt: Long = 1_788_807_940L,
        review: String = "A review",
    ): EntityRatingEvent {
        val coordinate = Address.assemble(PublicationIndexEvent.KIND, publisher, identifier)
        return EntityRatingEvent(
            id = id,
            pubKey = author,
            createdAt = createdAt,
            tags =
                arrayOf(
                    arrayOf("d", "books:$coordinate"),
                    arrayOf("m", "books"),
                    arrayOf("rating", "%.3f".format(stars / 5.0)),
                    arrayOf("s", stars.toString()),
                    arrayOf("a", coordinate),
                    arrayOf("k", PublicationIndexEvent.KIND.toString()),
                    arrayOf("p", publisher),
                ),
            content = review,
            sig = "sig",
        )
    }

    @Test
    fun aRatingIsConsumedAsAnAddressableNote() {
        val author = "b2".repeat(32)
        val event = rating("b3".repeat(32), author, "consumed-book", 5)

        assertTrue("LocalCache must accept kind 34259", consumePinned(event))

        val note = LocalCache.getAddressableNoteIfExists(event.addressTag())
        assertNotNull("the rating must land in the addressable index", note)
        assertEquals(event.id, note?.event?.id)
    }

    @Test
    fun aNewerRatingFromTheSameAuthorReplacesTheOlderOne() {
        val author = "b4".repeat(32)
        val first = rating("b5".repeat(32), author, "replaced-book", 1, createdAt = 1_000_000L)
        val second = rating("b6".repeat(32), author, "replaced-book", 5, createdAt = 2_000_000L)

        consumePinned(first)
        consumePinned(second)

        val note = LocalCache.getAddressableNoteIfExists(first.addressTag())
        assertEquals("the later rating wins the (pubkey, d) slot", second.id, note?.event?.id)
        assertEquals(5.0, (note?.event as EntityRatingEvent).stars())
    }

    @Test
    fun anOlderRatingDoesNotOverwriteANewerOne() {
        val author = "b7".repeat(32)
        val newer = rating("b8".repeat(32), author, "ordered-book", 5, createdAt = 2_000_000L)
        val older = rating("b9".repeat(32), author, "ordered-book", 1, createdAt = 1_000_000L)

        consumePinned(newer)
        consumePinned(older)

        val note = LocalCache.getAddressableNoteIfExists(newer.addressTag())
        assertEquals(newer.id, note?.event?.id)
    }

    @Test
    fun twoAuthorsRatingTheSameBookDoNotCollide() {
        val one = rating("c1".repeat(32), "c2".repeat(32), "shared-book", 5)
        val two = rating("c3".repeat(32), "c4".repeat(32), "shared-book", 2)

        consumePinned(one)
        consumePinned(two)

        assertEquals(one.id, LocalCache.getAddressableNoteIfExists(one.addressTag())?.event?.id)
        assertEquals(two.id, LocalCache.getAddressableNoteIfExists(two.addressTag())?.event?.id)
    }

    @Test
    fun theRatedPublicationIsConsumedToo() {
        // Without this the rating's target card could never resolve to a real title.
        val index =
            PublicationIndexEvent(
                id = "c5".repeat(32),
                pubKey = publisher,
                createdAt = 1_700_000_000L,
                tags = arrayOf(arrayOf("d", "wuthering-heights"), arrayOf("title", "Wuthering Heights")),
                content = "",
                sig = "sig",
            )

        assertTrue("LocalCache must accept kind 30040", consumePinned(index))
        assertEquals("Wuthering Heights", (LocalCache.getAddressableNoteIfExists(index.addressTag())?.event as PublicationIndexEvent).title())
    }

    @Test
    fun aRatingStaysANewThreadSoItReachesTheNewThreadsTab() {
        // The a/e tags point at what is being rated, not at a parent. If computeReplyTo ever
        // starts populating replyTo for this kind, isNewThread() flips and the card silently
        // leaves the New Threads tab — this test is the tripwire for that.
        val author = "c6".repeat(32)
        val event = rating("c7".repeat(32), author, "new-thread-book", 4)

        consumePinned(event)

        val note = LocalCache.getAddressableNoteIfExists(event.addressTag())!!
        assertTrue("replyTo must stay empty", note.replyTo.isNullOrEmpty())
        assertTrue("a rating is a top-level card", note.isNewThread())
    }

    @Test
    fun theRatingsToggleOwnsKind34259() {
        assertEquals(listOf(EntityRatingEvent.KIND), HomeFeedType.RATINGS.kinds)

        val disabled = HomeFeedType.disabledKinds(HomeFeedType.ALL - HomeFeedType.RATINGS)
        assertTrue("turning Ratings off must drop the kind from the home REQs", EntityRatingEvent.KIND in disabled)

        assertFalse(
            "with everything enabled nothing is dropped",
            EntityRatingEvent.KIND in HomeFeedType.disabledKinds(HomeFeedType.ALL),
        )
    }
}
