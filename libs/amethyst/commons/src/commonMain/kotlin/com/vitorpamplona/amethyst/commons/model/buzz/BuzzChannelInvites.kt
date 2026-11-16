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
package com.vitorpamplona.amethyst.commons.model.buzz

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Somebody added me to a channel: who did it, where, and when.
 *
 * [eventId] is the kind-44100 this was projected from — the identity the Notifications feed keys its
 * card on, so an invite is one row per relay verdict exactly like every other notification.
 */
@Immutable
class BuzzChannelInvite(
    val eventId: HexKey,
    val channelId: String,
    val relay: NormalizedRelayUrl,
    val actor: HexKey?,
    val createdAt: Long,
)

/**
 * One relay-signed membership verdict addressed to the viewer — a kind-44100 (`removed = false`) or a
 * kind-44101 (`removed = true`), flattened out of the event so the projection below can be a pure
 * function over a list and tested without a cache.
 */
@Immutable
class MembershipNotice(
    val eventId: HexKey,
    val channelId: String,
    val relay: NormalizedRelayUrl,
    val actor: HexKey?,
    val createdAt: Long,
    val removed: Boolean,
)

/** What a channel's kind-39000 says it is, once it has loaded. */
enum class ChannelClassification {
    /** `t = dm` — a real Buzz DM. Never an invite; the DM inbox owns it. */
    DM,

    /** Any other `t` — a named channel somebody put me in. */
    NAMED,

    /** The kind-39000 hasn't arrived yet, so we cannot tell the two apart. */
    UNKNOWN,
}

/**
 * Projections over the Buzz relay's membership notifications (`#p` = me), which are the *only*
 * enumeration of the channels a viewer belongs to on a Buzz relay.
 *
 * Membership there is server-side: another member issues the add, the relay writes the viewer into the
 * channel's kind-39002 roster, and the viewer can immediately read and post. The relay then addresses
 * them a kind-44100 whose body names the actor — and it emits the *same* kind for a self-join, with
 * `actor == me`, which is the only thing separating the two cases. A kind-44101 withdraws the
 * membership again.
 *
 * ### Why this is a projection and not a registry
 *
 * This used to be a process-wide mutable registry that discovery `record`ed into and classification
 * `remove`d from. Both halves of the state were derived from events the cache already held, so the
 * registry was a second source of truth that could — and did — drift from it: the classification's
 * removal was remembered nowhere, so any re-delivery of the same kind-44100 re-added an invite that had
 * already been withdrawn, and the prompt flickered in and out. Deriving instead means the answer is a
 * pure function of (notices, dismissals, joined list, channel types) and cannot disagree with the cache
 * that produced it.
 */
object BuzzChannelInvites {
    /**
     * The newest verdict per channel. Newest wins because membership is a running state, not a log: an
     * add followed by a remove is *not* a member, and a re-add after that is. A tie in `created_at`
     * resolves to the removal — the conservative side, since offering "Accept" on a membership the relay
     * has taken away is an action that cannot succeed.
     */
    fun latestPerChannel(notices: List<MembershipNotice>): Map<String, MembershipNotice> {
        val newest = HashMap<String, MembershipNotice>(notices.size)
        notices.forEach { notice ->
            val current = newest[notice.channelId]
            val wins =
                current == null ||
                    notice.createdAt > current.createdAt ||
                    (notice.createdAt == current.createdAt && notice.removed)
            if (wins) newest[notice.channelId] = notice
        }
        return newest
    }

    /**
     * Every channel the viewer is currently in (`channelId` -> the relay that vouched for it),
     * irrespective of who added them or what type the channel turns out to be.
     *
     * This is what the directory fetch iterates: a channel's type is only knowable once its kind-39000
     * has been fetched *by id*, so the fetch has to cover channels that will later be classified out.
     */
    fun currentMemberships(notices: List<MembershipNotice>): Map<String, NormalizedRelayUrl> =
        latestPerChannel(notices)
            .values
            .filterNot { it.removed }
            .associate { it.channelId to it.relay }

    /**
     * The channels somebody **else** put the viewer in that are still awaiting a decision, newest first.
     *
     * An entry is withheld when it is not a question:
     *  - the newest verdict is a removal — there is no membership left to accept;
     *  - the actor is the viewer, so this is a self-join, not somebody else's doing;
     *  - the channel is on the viewer's kind-10009 list ([joined]) — accepted already, and the ordinary
     *    Messages row owns it;
     *  - the viewer dismissed it ([dismissed]) — a local, reversible display choice;
     *  - [classify] does not (yet) say it is a named channel.
     *
     * That last rule is deliberately positive: an [ChannelClassification.UNKNOWN] channel is withheld
     * rather than shown. A Buzz DM arrives as the same kind-44100 as a channel add and is only told
     * apart once its kind-39000 lands, so surfacing on unknown means every new DM flashes up a "somebody
     * added you to a channel" card for as long as the directory fetch takes, and then withdraws it.
     * Waiting costs a beat on a genuine invite; not waiting is a wrong prompt on every DM.
     */
    fun pendingInvites(
        viewer: HexKey,
        notices: List<MembershipNotice>,
        dismissed: Set<String>,
        joined: Set<String>,
        classify: (channelId: String, relay: NormalizedRelayUrl) -> ChannelClassification,
    ): List<BuzzChannelInvite> =
        latestPerChannel(notices)
            .values
            .asSequence()
            .filterNot { it.removed }
            .filterNot { it.actor != null && it.actor.equals(viewer, ignoreCase = true) }
            .filterNot { it.channelId in dismissed || it.channelId in joined }
            .filter { classify(it.channelId, it.relay) == ChannelClassification.NAMED }
            .map { BuzzChannelInvite(it.eventId, it.channelId, it.relay, it.actor, it.createdAt) }
            .sortedByDescending { it.createdAt }
            .toList()

    /** [pendingInvites] keyed by the kind-44100 that produced each one, for per-note lookups. */
    fun pendingInvitesByEventId(
        viewer: HexKey,
        notices: List<MembershipNotice>,
        dismissed: Set<String>,
        joined: Set<String>,
        classify: (channelId: String, relay: NormalizedRelayUrl) -> ChannelClassification,
    ): Map<HexKey, BuzzChannelInvite> = pendingInvites(viewer, notices, dismissed, joined, classify).associateBy { it.eventId }
}
