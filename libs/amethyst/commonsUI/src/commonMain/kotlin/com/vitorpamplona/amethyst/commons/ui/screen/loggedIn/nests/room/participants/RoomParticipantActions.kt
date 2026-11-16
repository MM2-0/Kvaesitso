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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.participants

import com.vitorpamplona.quartz.nip01Core.core.nextCreatedAtToSupersede
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.endpoint
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.image
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.participants
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.relays
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.summary
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Pure builders for participant-list mutations on a kind-30312
 * audio room. All three return a republish template with the SAME
 * `d`-tag — the relay treats it as a replacement of [original].
 *
 * Extracted out of the screen Composable so they can be unit-tested
 * without an AccountViewModel / signer (the sign+broadcast path is
 * the only side effect, mirroring
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.edit.EditNestViewModel.buildEditTemplate]).
 *
 * Risk-mitigation: each builder reads ALL participants from
 * [original] and rebuilds the list with one row mutated; never
 * passes a smaller list. A future "remove ghost participants"
 * refactor needs to live elsewhere — these builders never
 * silently drop anyone.
 */
object RoomParticipantActions {
    /**
     * Promote [targetPubkey] to [newRole]. If they were already a
     * participant on the event, their existing tag is replaced; if
     * not (e.g. an audience member raising their hand), a new
     * `p`-tag is added at the bottom of the list.
     *
     * Refuses to demote the host — there's exactly one host per
     * NIP-53 audio room and the host's pubkey IS the event author,
     * so demoting them produces a corrupt event. Returns null in
     * that case.
     */
    fun setRole(
        original: MeetingSpaceEvent,
        targetPubkey: String,
        newRole: ROLE,
    ): EventTemplate<MeetingSpaceEvent>? {
        val all = original.participants()
        val targetExisting = all.firstOrNull { it.pubKey == targetPubkey }
        if (targetExisting?.role.equals(ROLE.HOST.code, ignoreCase = true) && newRole != ROLE.HOST) {
            return null
        }

        val mutated =
            if (targetExisting != null) {
                all.map {
                    if (it.pubKey == targetPubkey) it.copy(role = newRole.code) else it
                }
            } else {
                all + ParticipantTag(targetPubkey, null, newRole.code, null)
            }
        return rebuild(original, mutated, original.status() ?: StatusTag.STATUS.LIVE)
    }

    /**
     * Demote a speaker / moderator back to listener. Same guarantees
     * as [setRole] — host can't be demoted.
     *
     * If the target isn't currently in the participant list at all,
     * returns null — they're already a pure-audience listener and
     * re-publishing the kind-30312 to add them back as PARTICIPANT
     * would silently pin them to the room's participant tags forever
     * (the "promote on demote" bug, audit #1).
     */
    fun demoteToListener(
        original: MeetingSpaceEvent,
        targetPubkey: String,
    ): EventTemplate<MeetingSpaceEvent>? {
        val existing = original.participants().firstOrNull { it.pubKey == targetPubkey } ?: return null
        if (existing.role.equals(ROLE.PARTICIPANT.code, ignoreCase = true)) return null
        return setRole(original, targetPubkey, ROLE.PARTICIPANT)
    }

    /**
     * Drop [targetPubkey]'s p-tag from the room event entirely.
     * Mirrors nostrnests' kick follow-up (`updateRoomParticipant(_,
     * null)` after `kickUser` in `ProfileCard.tsx`): the kind-4312
     * boots them off the audio plane, this re-published kind-30312
     * keeps them out of the participant grid.
     *
     * Refuses to drop the host — same reasoning as [setRole]. Returns
     * null if the target isn't currently in the participant list (a
     * pure-audience kick has no list-mutation to do).
     */
    fun removeParticipant(
        original: MeetingSpaceEvent,
        targetPubkey: String,
    ): EventTemplate<MeetingSpaceEvent>? {
        val all = original.participants()
        val target = all.firstOrNull { it.pubKey == targetPubkey } ?: return null
        if (target.role.equals(ROLE.HOST.code, ignoreCase = true)) return null
        val remaining = all.filterNot { it.pubKey == targetPubkey }
        return rebuild(original, remaining, original.status() ?: StatusTag.STATUS.LIVE)
    }

    private fun rebuild(
        original: MeetingSpaceEvent,
        participants: List<ParticipantTag>,
        status: StatusTag.STATUS,
    ): EventTemplate<MeetingSpaceEvent> {
        val host =
            participants.firstOrNull { it.role.equals(ROLE.HOST.code, ignoreCase = true) }
                ?: ParticipantTag(original.pubKey, null, ROLE.HOST.code, null)
        val others = participants.filterNot { it.pubKey == host.pubKey }

        // Creating a room then immediately promoting in the same wall-clock
        // second produced the dreaded silent-no-op: both versions carried the
        // same createdAt and the tie-break picked one at random.
        //
        // The out-stamping form rather than awaitCreatedAtToSupersede: this is
        // reached from non-suspending Compose click handlers, and the stamp is
        // derived from the one event being acted on, so it runs at most a second
        // ahead of the clock and cannot drift the way a repeated republish does.
        val nextCreatedAt = nextCreatedAtToSupersede(original.createdAt, TimeUtils.now())

        return MeetingSpaceEvent.build(
            room = original.room().orEmpty(),
            status = status,
            service = original.service().orEmpty(),
            host = host,
            dTag = original.dTag(),
            createdAt = nextCreatedAt,
        ) {
            original.endpoint()?.let { endpoint(it) }
            original.summary()?.takeIf { it.isNotBlank() }?.let { summary(it) }
            original.image()?.takeIf { it.isNotBlank() }?.let { image(it) }
            // Preserve the room's `relays` tag — without it, the
            // republished kind-30312 fans out only to the host's
            // outbox, never reaching the room's own relay set
            // (e.g. nostrnests's fixed five reads). The audience
            // member would then never see their role grant and
            // wouldn't appear on stage for any other participant
            // listening on those relays.
            original.relays().takeIf { it.isNotEmpty() }?.let { relays(it) }
            if (others.isNotEmpty()) participants(others)
        }
    }
}
