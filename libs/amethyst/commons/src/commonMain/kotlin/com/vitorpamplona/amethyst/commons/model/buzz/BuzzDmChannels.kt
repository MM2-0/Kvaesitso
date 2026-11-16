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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide, per-viewer set of the Buzz **DM channels** the viewer belongs to — each a relay-generated
 * UUID (`channelId`) plus the relay that vouched for it.
 *
 * The deployed relay does not emit a queryable kind-41001; it enumerates a member's channels by
 * addressing each one a kind-44100 member-added notification (`#p` = me). The always-on
 * `BuzzDmDiscovery` subscribes that `#p=me` stream across the joined Buzz relays and records the
 * result here, so the always-on DM chat tail can keep those channels' messages warm in `LocalCache`
 * app-wide (which is what lets a Buzz DM surface on the Notifications tab and in push without the
 * viewer opening the conversation first).
 *
 * Kept **per-viewer** because the 44100 stream is `#p`-gated to its owner and the process can switch
 * accounts. Mutations are lock-guarded because discovery runs across several relay reader threads.
 * Like [BuzzDmRegistry] / [BuzzWorkspaces], a process-wide singleton.
 */
object BuzzDmChannels {
    private val lock = KmpLock()
    private val byViewer = HashMap<HexKey, MutableMap<String, NormalizedRelayUrl>>()
    private val mutableFlow = MutableStateFlow<Map<HexKey, Map<String, NormalizedRelayUrl>>>(emptyMap())

    /** Per-viewer discovered DM channels (`channelId` -> the relay it was discovered on). */
    val flow: StateFlow<Map<HexKey, Map<String, NormalizedRelayUrl>>> = mutableFlow

    /**
     * Records that [viewer] belongs to DM channel [channelId] on [relay]. Returns true when this is a
     * newly seen (viewer, channel) pair so callers can trigger a re-subscribe; a repeat that only
     * re-confirms the same relay returns false and does not churn the flow.
     */
    fun record(
        viewer: HexKey,
        channelId: String,
        relay: NormalizedRelayUrl,
    ): Boolean =
        lock.withLock {
            val channels = byViewer.getOrPut(viewer) { mutableMapOf() }
            if (channels[channelId] == relay) return@withLock false
            channels[channelId] = relay
            mutableFlow.value = snapshot()
            true
        }

    /**
     * Drops a channel that turned out not to be a DM. Discovery provisionally records every kind-44100
     * here so the channel's kind-39000 can be fetched by id; once that reveals a `t` other than `dm` the
     * entry is withdrawn, because the always-on DM tail must not silently subscribe the viewer to a named
     * channel somebody added them to (see [BuzzChannelInvites]).
     */
    fun remove(
        viewer: HexKey,
        channelId: String,
    ): Boolean =
        lock.withLock {
            val channels = byViewer[viewer] ?: return@withLock false
            if (channels.remove(channelId) == null) return@withLock false
            mutableFlow.value = snapshot()
            true
        }

    /**
     * Sets [viewer]'s whole DM set at once, replacing whatever was there.
     *
     * This is what discovery uses, because its input is a *recomputation* from the cache rather than a
     * stream of deltas: every pass sees the complete membership picture, so declaring the result is both
     * simpler and idempotent. Incremental [record]/[remove] against a recomputed set would ping-pong —
     * classification removes a named channel, the next pass re-derives it from the same kind-44100 and
     * re-adds it, and the flow churns forever with nothing having changed.
     *
     * Returns true when the set actually moved, so callers can skip work on a no-op pass.
     */
    fun replace(
        viewer: HexKey,
        channels: Map<String, NormalizedRelayUrl>,
    ): Boolean =
        lock.withLock {
            val current = byViewer[viewer]
            if (current == null && channels.isEmpty()) return@withLock false
            if (current != null && current == channels) return@withLock false

            if (channels.isEmpty()) {
                byViewer.remove(viewer)
            } else {
                byViewer[viewer] = channels.toMutableMap()
            }
            mutableFlow.value = snapshot()
            true
        }

    /** The DM channels [viewer] is in (`channelId` -> relay), possibly empty. */
    fun channelsFor(viewer: HexKey): Map<String, NormalizedRelayUrl> = mutableFlow.value[viewer] ?: emptyMap()

    private fun snapshot(): Map<HexKey, Map<String, NormalizedRelayUrl>> = byViewer.mapValues { it.value.toMap() }

    /** Test-only: clears all registry state so unit tests don't leak into each other. */
    fun clearForTesting() =
        lock.withLock {
            byViewer.clear()
            mutableFlow.value = emptyMap()
        }
}
