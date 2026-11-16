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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepostRenderabilityTest {
    private val id = "00".repeat(32)
    private val pubKey = "11".repeat(32)
    private val sig = "22".repeat(64)
    private val createdAt = 1_700_000_000L
    private val boostedEventId = "33".repeat(32)

    // Kind 16767 (Ditto's profile theme) was the example here until Quartz typed it.
    // This one is unassigned, so no Quartz class will claim it.
    private val unknownKind = "19999"

    private fun genericRepost(tags: Array<Array<String>>) = GenericRepostEvent(id, pubKey, createdAt, tags, "", sig)

    private fun repost(tags: Array<Array<String>>) = RepostEvent(id, pubKey, createdAt, tags, "", sig)

    @Test
    fun hidesGenericRepostOfUnknownKind() {
        // An unassigned kind has no Quartz class → cannot render → hide.
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", unknownKind)))
        assertFalse(event.isRenderableRepost())
    }

    @Test
    fun showsGenericRepostOfKnownKind() {
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "1")))
        assertTrue(event.isRenderableRepost())
    }

    @Test
    fun showsGenericRepostWithoutKindTag() {
        // Conservative: with no `k` tag we cannot prove the inner kind is unknown, so keep it.
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId)))
        assertTrue(event.isRenderableRepost())
    }

    @Test
    fun hidesKind6RepostOfUnknownKind() {
        val event = repost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", unknownKind)))
        assertFalse(event.isRenderableRepost())
    }

    @Test
    fun hidesRepostsOfTypedKindsWithoutACard() {
        // CI job results (9841) are typed, but would render as a bare log tail.
        val event = genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "9841")))
        assertFalse(event.isRenderableRepost())
        TYPED_WITHOUT_A_CARD.forEach { kind ->
            assertFalse(genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", kind.toString()))).isRenderableRepost(), "kind $kind")
        }
    }

    @Test
    fun everyKindWithoutACardIsActuallyTyped() {
        // The set exists to undo isKnownKind for typed kinds; an untyped entry is already hidden
        // and would only be dead weight, most likely a typo.
        val untyped = TYPED_WITHOUT_A_CARD.filterNot(EventFactory::isKnownKind)
        assertTrue(untyped.isEmpty(), "not typed by Quartz: $untyped")
    }

    // Kinds 30301, 30302 and 38385 are shared: whether a repost of one shows anything depends on
    // the class the embedded event parses to, not on the kind.
    private fun embedded(
        kind: Int,
        vararg tags: Array<String>,
        content: String = "",
    ): String = EventFactory.create<Event>("44".repeat(32), pubKey, createdAt, kind, arrayOf(arrayOf("d", "x"), *tags), content, sig).toJson()

    private fun repostOf(
        kind: Int,
        json: String,
    ) = GenericRepostEvent(id, pubKey, createdAt, arrayOf(arrayOf("e", boostedEventId), arrayOf("k", kind.toString())), json, sig)

    @Test
    fun showsRepostsOfTheCardedShapesOfSharedKinds() {
        val board = embedded(30301, arrayOf("title", "Roadmap"), arrayOf("col", "todo", "To do", "0"))
        val verdict = embedded(30301, arrayOf("i", "com.example.wallet"), arrayOf("status", "reproducible"))
        val card = embedded(30302, arrayOf("title", "Fix it"), arrayOf("a", "30301:$pubKey:roadmap"))
        val mostro = embedded(38385, arrayOf("z", "info"), arrayOf("mostro_version", "0.12.8"))

        assertTrue(repostOf(30301, board).isRenderableRepost(), "board")
        assertTrue(repostOf(30301, verdict).isRenderableRepost(), "verification")
        assertTrue(repostOf(30302, card).isRenderableRepost(), "kanban card")
        assertTrue(repostOf(38385, mostro).isRenderableRepost(), "mostro info")
    }

    @Test
    fun hidesRepostsOfTheOtherAppsShapesOfSharedKinds() {
        // The encrypted planner's task on 30301, Fieldbook's membership on 30302, a Paygress
        // revocation on 38385: EventFactory builds UnrecognizedKind…Event for each, which no card draws.
        val planner = embedded(30301, arrayOf("b", "ff".repeat(32)), arrayOf("col", "day"), arrayOf("status", "open"), content = "ciphertext")
        val membership = embedded(30302, arrayOf("a", "30300:$pubKey:team"), arrayOf("client", "fieldbook"))
        val paygress = embedded(38385, arrayOf("t", "paygress"))

        assertFalse(repostOf(30301, planner).isRenderableRepost(), "planner")
        assertFalse(repostOf(30302, membership).isRenderableRepost(), "membership")
        assertFalse(repostOf(38385, paygress).isRenderableRepost(), "paygress")
    }

    @Test
    fun hidesRepostsOfSharedKindsThatEmbedNothing() {
        // Without the event there is no telling a board from a planner task, and the planner is
        // the commoner of the two.
        KINDS_WITH_A_CARD_BY_CLASS.forEach { kind ->
            assertFalse(genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", kind.toString()))).isRenderableRepost(), "kind $kind")
            assertFalse(repost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", kind.toString()))).isRenderableRepost(), "kind 6 of $kind")
        }
    }

    @Test
    fun aPodcastEpisodeRepostIsShownUnlessItEmbedsAnotherAppsShape() {
        val episode = embedded(30054, arrayOf("title", "Episode 1"), arrayOf("audio", "https://example.com/ep-1.mp3"))
        val progress = embedded(30054, arrayOf("a", "30023:$pubKey:x"), arrayOf("position", "733"))
        val unknown = embedded(30054, arrayOf("x", "y"))

        assertTrue(repostOf(30054, episode).isRenderableRepost(), "episode")
        assertTrue(repostOf(30054, progress).isRenderableRepost(), "drss progress has a card")
        assertFalse(repostOf(30054, unknown).isRenderableRepost(), "another app's shape")
        // Nothing embedded: the episode is by far the usual shape, so the repost stays.
        assertTrue(genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "30054"))).isRenderableRepost(), "bare")
    }

    @Test
    fun drssRepostsAreShownForDrssShapesOnly() {
        val folder = embedded(30101, arrayOf("title", "DRSS lists"), arrayOf("a", "30004:$pubKey:podcasts"))
        val round = embedded(30101, arrayOf("title", "Rodada 2"), arrayOf("projeto", "30100:$pubKey:x"))
        val record = embedded(31985, arrayOf("L", "systems.buildtall.provenance"), arrayOf("p", pubKey))
        val rating = embedded(31985, arrayOf("k", "isbn"), arrayOf("rating", "0.8"))

        assertTrue(repostOf(30101, folder).isRenderableRepost(), "drss folder")
        assertFalse(repostOf(30101, round).isRenderableRepost(), "voting round")
        assertTrue(repostOf(31985, record).isRenderableRepost(), "drss provenance")
        assertFalse(repostOf(31985, rating).isRenderableRepost(), "Ditto rating")
        assertTrue(genericRepost(arrayOf(arrayOf("e", boostedEventId), arrayOf("k", "31341"))).isRenderableRepost(), "show")
    }

    @Test
    fun sharedKindsAreDecidedByClassNotListedAsCardless() {
        assertTrue((KINDS_WITH_A_CARD_BY_CLASS intersect TYPED_WITHOUT_A_CARD).isEmpty())
    }

    @Test
    fun nonRepostReturnsFalse() {
        val textNote: Event = EventFactory.create(id, pubKey, createdAt, 1, emptyArray(), "", sig)
        assertFalse(textNote.isRenderableRepost())
    }

    @Test
    fun nullReturnsFalse() {
        val nothing: Event? = null
        assertFalse(nothing.isRenderableRepost())
    }

    @Test
    fun aRepostOfAnOpenMarketsCollectionOrOptionShowsButNotAShopstrCartOrEscrowRecord() {
        val collection = embedded(30405, arrayOf("title", "Summit"), arrayOf("a", "30402:$pubKey:socks"))
        val option = embedded(30406, arrayOf("title", "Worldwide"), arrayOf("price", "10", "USD"), arrayOf("country", "US"))
        // Shopstr's cart is all ciphertext; its escrow record names no title, service or country.
        val cart = embedded(30405, content = "ciphertext")
        val escrow = embedded(30406, arrayOf("p", pubKey), content = "ciphertext")

        assertTrue(repostOf(30405, collection).isRenderableRepost(), "collection")
        assertTrue(repostOf(30406, option).isRenderableRepost(), "shipping option")
        assertFalse(repostOf(30405, cart).isRenderableRepost(), "cart")
        assertFalse(repostOf(30406, escrow).isRenderableRepost(), "escrow record")
    }
}
