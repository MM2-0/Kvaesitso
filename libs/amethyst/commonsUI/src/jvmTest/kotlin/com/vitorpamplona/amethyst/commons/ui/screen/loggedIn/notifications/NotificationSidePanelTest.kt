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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import com.vitorpamplona.amethyst.commons.feeds.Card
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSidePanelTest {
    private data class FakeCard(
        val at: Long,
        val key: String,
    ) : Card {
        override fun createdAt(): Long = at

        override fun id(): String = key
    }

    @Test
    fun `counts only the cards that landed after the fold`() {
        val cards = listOf(FakeCard(1_000, "a"), FakeCard(1_500, "b"), FakeCard(2_000, "c"))

        assertEquals(2, countNewerThan(cards, sinceSeconds = 1_000))
    }

    @Test
    fun `a card stamped exactly at the fold is not missed`() {
        val cards = listOf(FakeCard(1_000, "a"))

        assertEquals(0, countNewerThan(cards, sinceSeconds = 1_000))
    }

    @Test
    fun `a restored fold counts only what lands after the restore`() {
        // A fold restored from disk restarts its clock at the restore (see
        // NotificationPanelCollapsePreferences): cards that landed while the app was closed are
        // older than the restore and read as already past, not missed.
        val restoredAt = 5_000L
        val cards = listOf(FakeCard(1_000, "while-closed"), FakeCard(6_000, "after-restore"))

        assertEquals(1, countNewerThan(cards, sinceSeconds = restoredAt))
    }

    @Test
    fun `no fold instant reads as nothing missed`() {
        // Expanded, or the saved fold not loaded yet: no clock, nothing counts.
        val cards = listOf(FakeCard(9_999, "a"))

        assertEquals(0, countNewerThan(cards, sinceSeconds = null))
    }

    @Test
    fun `no feed yet reads as nothing missed`() {
        assertEquals(0, countNewerThan(cards = null, sinceSeconds = 1_000))
        assertEquals(0, countNewerThan(cards = emptyList(), sinceSeconds = 1_000))
    }

    @Test
    fun `the badge shows the raw count up to the cap`() {
        assertEquals("1", missedBadgeLabel(1))
        assertEquals("99", missedBadgeLabel(99))
    }

    @Test
    fun `the badge caps past 99 at 99+`() {
        // Same cap as the chat unread pills: a busy afternoon must not blow out the strip.
        assertEquals("99+", missedBadgeLabel(100))
        assertEquals("99+", missedBadgeLabel(1_234))
    }
}
