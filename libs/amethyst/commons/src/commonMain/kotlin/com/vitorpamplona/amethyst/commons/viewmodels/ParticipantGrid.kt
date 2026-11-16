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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE

/**
 * One row in the room's participant grid. Combines the static
 * `p`-tag role (from the kind-30312 event) with the dynamic
 * presence flags (from the per-pubkey kind-10312 aggregator).
 *
 * Pure data — UI just renders it.
 */
@Immutable
data class RoomMember(
    val pubkey: String,
    /** From the kind-30312 `p`-tag; null when the user is pure audience. */
    val role: ROLE?,
    /** Last seen second from the kind-10312 ledger; null when the user has never emitted presence. */
    val lastSeenSec: Long?,
    val handRaised: Boolean,
    val muted: Boolean?,
    val publishing: Boolean,
    /**
     * `true` when the user is in `event.participants()` BUT has no
     * recent presence in the aggregator. nostrnests greys out these
     * "members never joined" — match for parity.
     */
    val absent: Boolean,
)

/** Two-column layout: speakers up top, audience below. */
@Immutable
data class ParticipantGrid(
    val onStage: List<RoomMember>,
    val audience: List<RoomMember>,
) {
    companion object {
        val Empty = ParticipantGrid(emptyList(), emptyList())
    }
}

/**
 * Pure projection — given the event's participants + the
 * per-pubkey presence map, build the grid.
 *
 *   * On-stage: anyone whose `p`-tag role is HOST/MODERATOR/SPEAKER
 *     AND whose latest presence advertises `onstage != false`. A
 *     speaker who explicitly emitted `onstage=0` ("step off the
 *     stage") drops to audience without losing their role tag.
 *
 *     Caveat: a presence event is only honoured for the on-stage
 *     gate when it's NEWER than the role grant ([roleGrantSec],
 *     conservatively the kind-30312 `created_at`). Otherwise a
 *     freshly-promoted audience member whose pre-promotion
 *     `kind-10312` carried `onstage=0` would be pinned to the
 *     audience tab until they re-heartbeat — which on nostrnests
 *     never happens, since audience members there don't toggle
 *     their `onstage` flag back on after a role bump. Stale
 *     presence ⇒ trust the role.
 *   * Audience: every pubkey with recent presence that isn't on
 *     stage, plus participant-tagged users who haven't emitted
 *     presence yet (rendered as `absent = true`).
 *
 * [hostPubkey] is the kind-30312 event signer. Per the EGG-07
 * convention (and nostrnests' production behaviour), the room
 * author is the implicit host even when they don't self-tag.
 * When the author isn't already in [participants], a synthetic
 * `["p", hostPubkey, "", "host"]` tag is prepended so the
 * presence-aware on-stage/audience rules apply uniformly —
 * including the "host stepped off stage" case (presence with
 * `onstage=0` → drops to audience, role stays HOST).
 */
fun buildParticipantGrid(
    participants: List<ParticipantTag>,
    presences: Map<String, RoomPresence>,
    hostPubkey: HexKey? = null,
    roleGrantSec: Long = 0L,
): ParticipantGrid {
    val effectiveParticipants =
        if (hostPubkey != null && participants.none { it.pubKey == hostPubkey }) {
            listOf(ParticipantTag(hostPubkey, null, ROLE.HOST.code, null)) + participants
        } else {
            participants
        }

    val onStage = mutableListOf<RoomMember>()
    val audience = mutableListOf<RoomMember>()

    val seen = mutableSetOf<String>()
    for (p in effectiveParticipants) {
        seen += p.pubKey
        val pres = presences[p.pubKey]
        val role = p.effectiveRole()
        val canSpeak = p.canSpeak()
        // Stale-presence override: trust the role when the presence
        // event predates the role grant. See KDoc above for why.
        val presenceIsFresh = pres != null && pres.updatedAtSec >= roleGrantSec
        val onstageFlag = if (presenceIsFresh) pres.onstage else true
        val member =
            RoomMember(
                pubkey = p.pubKey,
                role = role,
                lastSeenSec = pres?.updatedAtSec,
                handRaised = pres?.handRaised == true,
                muted = pres?.muted,
                publishing = pres?.publishing == true,
                absent = pres == null,
            )
        if (canSpeak && onstageFlag) onStage += member else audience += member
    }
    // Pure-audience members — present in the kind-10312 ledger but
    // not in the kind-30312 `p`-tags.
    for ((pubkey, pres) in presences) {
        if (pubkey in seen) continue
        audience +=
            RoomMember(
                pubkey = pubkey,
                role = null,
                lastSeenSec = pres.updatedAtSec,
                handRaised = pres.handRaised,
                muted = pres.muted,
                publishing = pres.publishing,
                absent = false,
            )
    }
    return ParticipantGrid(onStage = onStage, audience = audience)
}
