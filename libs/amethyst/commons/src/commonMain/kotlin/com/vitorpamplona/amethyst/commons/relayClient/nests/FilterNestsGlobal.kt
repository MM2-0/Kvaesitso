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
package com.vitorpamplona.amethyst.commons.relayClient.nests

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Window the lobby looks back for kind-10312 presence events. Matches
 * the [NestsFeedFilter] freshness gate so the wire fetch and the local
 * filter agree on what "live" means. Tighter than the room-event REQ
 * (1 week) since presence is a high-volume heartbeat stream we only
 * need recent samples of.
 */
private const val PRESENCE_LOOKBACK_SECONDS = 10L * 60L

/**
 * Lobby-wide presence probe so [NestsFeedFilter] can hide OPEN rooms
 * whose host crashed without flipping status to CLOSED. Without this
 * the only path that fetched kind-10312 was per-room when a card
 * mounted, which forced one full assembler subscription per visible
 * card just to color the badge.
 *
 * Returned as a single REQ per relay so it composes with whatever
 * room-event filter the active TopFilter built (global, follows,
 * authors, hashtag, geohash, community).
 */
fun filterNestsPresence(relay: NormalizedRelayUrl): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.LIVE_ROOMS,
                kinds = listOf(MeetingRoomPresenceEvent.KIND),
                limit = 500,
                since = TimeUtils.now() - PRESENCE_LOOKBACK_SECONDS,
            ),
    )

fun filterNestsGlobal(
    relays: GlobalTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (relays.set.isEmpty()) return emptyList()

    return relays.set
        .map {
            val roomsSince = since?.get(it.key)?.time ?: defaultSince
            listOf(
                RelayBasedFilter(
                    relay = it.key,
                    filter =
                        ExplainedFilter(
                            purpose = SubPurpose.LIVE_ROOMS,
                            kinds = listOf(MeetingSpaceEvent.KIND, MeetingRoomEvent.KIND),
                            limit = 300,
                            since = roomsSince ?: TimeUtils.oneWeekAgo(),
                        ),
                ),
                filterNestsPresence(it.key),
            )
        }.flatten()
}
