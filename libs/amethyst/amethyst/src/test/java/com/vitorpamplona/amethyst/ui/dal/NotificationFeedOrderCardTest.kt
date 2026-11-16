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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.feeds.DefaultFeedOrderCard
import com.vitorpamplona.amethyst.commons.feeds.NotificationFeedOrderCard
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.notifications.ChannelInviteCard
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Notifications tab's card order. An unanswered invite is a standing question, so it outranks every
 * dated row no matter how old it is; everything else keeps the ordinary newest-first order.
 *
 * A plain `created_at` sort is what would let a week of reactions bury a decision the user still has to
 * make — and, once the feed passes `limit()`, page it off the end entirely.
 */
class NotificationFeedOrderCardTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!

    /** A stand-in dated card. Only [Card.createdAt] and [Card.id] matter to the comparator. */
    private class DatedCard(
        private val createdAt: Long,
        private val id: String,
    ) : Card {
        override fun createdAt() = createdAt

        override fun id() = id
    }

    private fun invite(
        id: String,
        createdAt: Long,
    ) = ChannelInviteCard(Note(id), BuzzChannelInvite(id, "chan-$id", relay, null, createdAt))

    @Test
    fun `a stale invite still outranks every dated row`() {
        val cards =
            listOf(
                DatedCard(9_000L, "fresh-reaction"),
                invite("old-invite", 1_000L),
                DatedCard(8_000L, "older-reaction"),
            )

        assertEquals(
            listOf("old-invite", "fresh-reaction", "older-reaction"),
            cards.sortedWith(NotificationFeedOrderCard).map { it.id() },
        )
    }

    @Test
    fun `invites sort newest-first among themselves`() {
        val cards =
            listOf(
                invite("older", 1_000L),
                invite("newest", 3_000L),
                invite("middle", 2_000L),
            )

        assertEquals(
            listOf("newest", "middle", "older"),
            cards.sortedWith(NotificationFeedOrderCard).map { it.id() },
        )
    }

    @Test
    fun `dated rows keep the default order behind the invites`() {
        val cards = listOf(DatedCard(1_000L, "b"), DatedCard(3_000L, "a"), DatedCard(2_000L, "c"))

        assertEquals(
            cards.sortedWith(DefaultFeedOrderCard).map { it.id() },
            cards.sortedWith(NotificationFeedOrderCard).map { it.id() },
        )
    }

    @Test
    fun `the comparator is a total order so sorting never throws`() {
        // Cards tie on created_at routinely (a batch of reactions lands in the same second), and an
        // inconsistent comparator makes TimSort throw "Comparison method violates its general contract".
        val cards =
            listOf(
                DatedCard(1_000L, "b"),
                DatedCard(1_000L, "a"),
                invite("x", 1_000L),
                invite("y", 1_000L),
            )

        assertEquals(
            listOf("x", "y", "a", "b"),
            cards.sortedWith(NotificationFeedOrderCard).map { it.id() },
        )
    }
}
