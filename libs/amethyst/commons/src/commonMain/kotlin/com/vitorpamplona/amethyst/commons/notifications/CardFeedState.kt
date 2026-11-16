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
package com.vitorpamplona.amethyst.commons.notifications

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.model.CombinedZap
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvite
import com.vitorpamplona.amethyst.commons.model.chats.ConcordDirectInviteNote
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

@Immutable
class BadgeCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

@Immutable
class NoteCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

@Immutable
class ZapUserSetCard(
    val user: User,
    val zapEvents: ImmutableList<CombinedZap>,
) : Card {
    val createdAt = zapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L

    override fun createdAt(): Long = createdAt

    override fun id() = user.pubkeyHex + "U" + createdAt
}

/**
 * Per-sender NIP-61 nutzap aggregate, parallel to [ZapUserSetCard].
 * Carries raw kind:9321 [Note]s rather than [CombinedZap] pairs because
 * a nutzap is a single event (no request/receipt split like lightning).
 * Rendered with a cashu icon so the user can tell at a glance that it's
 * a cashu rail, not a lightning rail.
 */
@Immutable
class NutzapUserSetCard(
    val user: User,
    val nutzapEvents: ImmutableList<Note>,
) : Card {
    val createdAt = nutzapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L

    override fun createdAt(): Long = createdAt

    // Suffix differs from ZapUserSetCard so the two never collide on id.
    override fun id() = user.pubkeyHex + "N" + createdAt
}

@Immutable
class MultiSetCard(
    val note: Note,
    val boostEvents: ImmutableList<Note>,
    val likeEvents: ImmutableList<Note>,
    val zapEvents: ImmutableList<CombinedZap>,
    val nutzapEvents: ImmutableList<Note> = persistentListOf(),
) : Card {
    val maxCreatedAt =
        maxOf(
            zapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
            likeEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
            boostEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
            nutzapEvents.maxOfOrNull { it.createdAt() ?: 0L } ?: 0L,
        )

    val minCreatedAt =
        minOf(
            zapEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
            likeEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
            boostEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
            nutzapEvents.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE,
        )

    val likeEventsByType =
        likeEvents
            .groupBy {
                it.event
                    ?.content
                    ?.firstFullCharOrEmoji(ImmutableListOfLists(it.event?.tags ?: emptyArray()))
                    ?: "+"
            }.mapValues { it.value.toImmutableList() }
            .toImmutableMap()

    override fun createdAt(): Long = maxCreatedAt

    override fun id() = note.idHex + "X" + maxCreatedAt + "X" + minCreatedAt
}

@Immutable
class MessageSetCard(
    val note: Note,
) : Card {
    override fun createdAt(): Long = note.createdAt() ?: 0L

    override fun id() = note.idHex
}

/**
 * A Concord Direct Invite (CORD-05 §6) waiting on an answer. Renders the same Accept / Decline card
 * as the Concord hub; answering it drops the row on the next rebuild. Sorted ahead
 * of dated cards like [ChannelInviteCard], so an old invite can't sink into history.
 */
class ConcordDirectInviteNotificationCard(
    val note: ConcordDirectInviteNote,
) : Card {
    override fun createdAt(): Long = note.createdAt()

    override fun id() = note.idHex
}

/**
 * "Somebody added you to a channel" — a relay-signed kind-44100 that is still unanswered, carrying the
 * [invite] the projection resolved it to (who did it, which channel, on which relay).
 *
 * A question rather than a dated event, so [com.vitorpamplona.amethyst.commons.feeds.NotificationFeedOrderCard]
 * sorts these ahead of everything else instead of letting an old one sink into history. It still holds
 * its [note], so it dedups, scrolls-to and pages exactly like every other card.
 */
@Immutable
class ChannelInviteCard(
    val note: Note,
    val invite: BuzzChannelInvite,
) : Card {
    override fun createdAt(): Long = invite.createdAt

    override fun id() = note.idHex
}

/**
 * Checks if this card contains a specific event ID.
 * Used for scrolling to a notification from a push notification intent.
 */
fun Card.containsEventId(eventId: String): Boolean =
    when (this) {
        is NoteCard -> {
            note.idHex == eventId
        }

        is BadgeCard -> {
            note.idHex == eventId
        }

        is MessageSetCard -> {
            note.idHex == eventId
        }

        is ChannelInviteCard -> {
            note.idHex == eventId
        }

        is ZapUserSetCard -> {
            zapEvents.any { it.response.idHex == eventId || it.request.idHex == eventId }
        }

        is NutzapUserSetCard -> {
            nutzapEvents.any { it.idHex == eventId }
        }

        is MultiSetCard -> {
            note.idHex == eventId ||
                zapEvents.any { it.response.idHex == eventId || it.request.idHex == eventId } ||
                likeEvents.any { it.idHex == eventId } ||
                boostEvents.any { it.idHex == eventId } ||
                nutzapEvents.any { it.idHex == eventId }
        }

        else -> {
            false
        }
    }
