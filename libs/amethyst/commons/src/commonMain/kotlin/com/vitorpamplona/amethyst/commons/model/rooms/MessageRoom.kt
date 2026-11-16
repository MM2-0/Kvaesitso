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
package com.vitorpamplona.amethyst.commons.model.rooms

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NotesGatherer
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollTallyPolicy
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.marmot.foundation.appEvents.MarmotCustomEmoji
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * A conversation only its members can read: a Marmot group, a Concord channel.
 *
 * The room owns its messages and everything said about them. A reaction, a deletion, a poll vote
 * or an admin removal of one of its messages travels inside the room, encrypted to its members,
 * and is kept with the room. None of it goes through the account's public paths, which publish
 * signed events naming the message to relays: Account refuses a note that is in a room
 * ([isInRoom]), so an action that forgets to ask the room fails instead of leaking.
 *
 * Shared UI reaches the room through the note ([roomFor]), never by asking which protocol the
 * note came from. Operations take the [Account] because a room can be shared by every account on
 * the device (a Concord channel lives in the global cache), while the keys to write in it are the
 * account's. Each returns false when nothing was sent.
 */
interface MessageRoom : NotesGatherer {
    /** True when [account] is a member of this room, and so may read and write it. */
    fun isReadBy(account: Account): Boolean

    /** Reacts to [target] with [reaction]. */
    suspend fun react(
        account: Account,
        target: Note,
        reaction: String,
        notices: UserNoticeSink,
    ): Boolean

    /** Retracts [notes], which must be [account]'s own messages or reactions in this room. */
    suspend fun delete(
        account: Account,
        notes: List<Note>,
    ): Boolean

    /** True when [account] may edit [note] from the room's composer. */
    fun canEdit(
        account: Account,
        note: Note,
    ): Boolean

    /** False when the room does not carry polls, or [poll] breaks the room's rules for them. */
    fun acceptsPoll(poll: PollEvent): Boolean = false

    /** True when a poll's author may vote in it here (Amethyst's public polls say no). */
    fun pollAuthorMayVote(): Boolean = false

    /** The rules this room's members tally [poll] by. */
    fun pollTallyPolicy(poll: PollEvent): PollTallyPolicy = PollTallyPolicy.from(poll)

    /** Votes for [optionCodes] on [poll], replacing any earlier vote. */
    suspend fun respondToPoll(
        account: Account,
        poll: Note,
        optionCodes: Set<String>,
    ): Boolean = false

    /**
     * True when a report of one of this room's messages stays among its members ([report]),
     * false when it goes out publicly about the author. Only a public report also blocks the
     * author: a report kept in the room is for its admins to review.
     */
    fun keepsReports(): Boolean = false

    /**
     * Reports [note], a message in this room, for [type], with [explanation] as free text.
     *
     * A room that keeps reports among its members (a Marmot group) sends one there, where every
     * member sees it and who sent it. This default is for a room that keeps none: it reports the
     * message's author publicly, naming no message, which is how a private message was reported
     * before rooms owned their messages.
     */
    suspend fun report(
        account: Account,
        note: Note,
        type: ReportType,
        explanation: String,
    ): Boolean {
        val author = note.author ?: return false
        account.report(author, type, explanation)
        return true
    }

    /** The reports on [note] that [account], as an admin of this room, may dismiss. */
    fun reportsToDismiss(
        account: Account,
        note: Note,
    ): List<Note> = emptyList()

    /** Dismisses [reports] for every member, as a room admin. It neither hides nor deletes their target. */
    suspend fun dismissReports(
        account: Account,
        reports: List<Note>,
    ): Boolean = false

    /** True when [account] may remove [note], someone else's message, for every member. */
    fun canRemoveForEveryone(
        account: Account,
        note: Note,
    ): Boolean = false

    /** Removes [note] for every member, as a room admin. */
    suspend fun removeForEveryone(
        account: Account,
        note: Note,
    ): Boolean = false
}

/**
 * The room of [account] this note belongs to, or null for a note outside every room.
 *
 * A message is in its room ([NotesGatherer]). A reaction, deletion or vote is not, so a private
 * one is placed by the message it points at.
 */
fun Note.roomFor(account: Account): MessageRoom? =
    ownRoom { it.isReadBy(account) }
        ?: if (isPlacedByTarget()) replyTo?.firstNotNullOfOrNull { it.ownRoom { room -> room.isReadBy(account) } } else null

/**
 * [user]'s reactions to this note that are the same reaction as [reaction]. A custom emoji matches by
 * shortcode: in a group each reaction carries its own encrypted copy of the picture under its own
 * URL, so comparing whole keys never finds the reaction to retract.
 */
fun Note.reactionsOfAuthorLike(
    user: User,
    reaction: String,
): List<Note> {
    val shortcode = MarmotCustomEmoji.reactionShortcode(reaction) ?: return allReactionsOfContentByAuthor(user, reaction)
    return reactions.flatMap { (type, notes) ->
        if (MarmotCustomEmoji.reactionShortcode(type) == shortcode) notes.filter { it.author == user } else emptyList()
    }
}

/** True when this note is in any room, whichever account reads it. See [MessageRoom]. */
fun Note.isInRoom(): Boolean = ownRoom { true } != null || (isPlacedByTarget() && replyTo?.any { it.ownRoom { true } != null } == true)

/**
 * A private note outside every room is placed by the message it points at, unless it is itself a
 * message of a private conversation: a NIP-17 DM that quotes a group message belongs to the DM,
 * and acting on it must not send anything into that group.
 */
private fun Note.isPlacedByTarget(): Boolean = isPrivateRumor() && inGatherers?.none { it is Chatroom } != false

private inline fun Note.ownRoom(accept: (MessageRoom) -> Boolean): MessageRoom? = inGatherers?.firstNotNullOfOrNull { (it as? MessageRoom)?.takeIf(accept) }
