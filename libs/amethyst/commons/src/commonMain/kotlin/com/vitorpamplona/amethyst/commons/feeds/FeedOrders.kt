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

import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.notifications.ChannelInviteCard
import com.vitorpamplona.amethyst.commons.notifications.ConcordDirectInviteNotificationCard
import com.vitorpamplona.quartz.nip01Core.core.Event

val DefaultFeedOrderEvent: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }

val DefaultFeedOrderCard: Comparator<Card> =
    compareByDescending<Card> { it.createdAt() }.thenBy { it.id() }

/**
 * Notifications order: unanswered channel invites first, then newest-first like everything else.
 *
 * An invite is a standing question — it stays actionable until it is answered — so ranking it purely by
 * `created_at` would let a week of reactions bury a decision the user still has to make, and a long
 * enough feed could page it off the end entirely. Everything else on the tab is a dated event and keeps
 * the ordinary ordering. Pending is the only state that reaches a card: answering one drops it from the
 * projection, so it never lingers at the top.
 */
val NotificationFeedOrderCard: Comparator<Card> =
    compareBy<Card> { if (it is ChannelInviteCard || it is ConcordDirectInviteNotificationCard) 0 else 1 }
        .thenByDescending { it.createdAt() }
        .thenBy { it.id() }

// Snapshots createdAt once per note so the comparator stays consistent even if
// another thread swaps a Note's event mid-sort (e.g. a newer AddressableEvent
// arriving from a relay). Avoids TimSort's "Comparison method violates its
// general contract!" IllegalArgumentException.
fun Iterable<Note>.sortedByDefaultFeedOrder(): List<Note> =
    map { it to (it.createdAt() ?: 0L) }
        .sortedWith(compareByDescending<Pair<Note, Long>> { it.second }.thenBy { it.first.idHex })
        .map { it.first }
