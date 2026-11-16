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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** What kind of plane an address belongs to. */
enum class ConcordPlaneKind {
    CONTROL,
    CHANNEL,
}

/**
 * A known Concord plane: its kind, community, optional channel, and what it takes
 * to open its wraps — the stream [address] they must be authored by, and the
 * [readConversationKey] their content decrypts under.
 *
 * The two are separate fields rather than one [GroupKey] because a split Control
 * Plane separates them (CORD-01, Write-Restricted Streams): the address is the
 * staff-held signer's pubkey, while the read key derives from the `community_root`
 * every member holds. On a channel plane and a legacy Control Plane they are the
 * two halves of the same key.
 */
class ConcordPlane(
    val kind: ConcordPlaneKind,
    val communityId: HexKey,
    val channelId: ConcordChannelId?,
    val address: HexKey,
    val readConversationKey: ByteArray,
) {
    constructor(
        kind: ConcordPlaneKind,
        communityId: HexKey,
        channelId: ConcordChannelId?,
        key: GroupKey,
    ) : this(kind, communityId, channelId, key.publicKeyHex, key.conversationKey)
}

/** The routed result of opening an inbound wrap that belonged to a known plane. */
class RoutedRumor(
    val plane: ConcordPlane,
    val opened: OpenedStreamEvent,
)

/**
 * Maps derived plane addresses (`group_key.pk`) to the keys that open them, so an
 * inbound kind-1059 wrap can be recognized as Concord traffic and decrypted with
 * the right per-plane key.
 *
 * This is the counterpart to [ConcordChannelListState] on the read path: the list
 * gives the community secrets, and this registry expands them into the concrete
 * plane addresses to watch. Because a Concord wrap's `p` tag is ephemeral, address
 * matching (`wrap.pubkey` → registered plane) is the only way to route it — a
 * non-member never registers the address, so they never decrypt.
 *
 * Control-plane addresses are known from a community entry alone; channel-plane
 * addresses become known only after the Control Plane folds ([registerChannels]).
 * Thread-safe so the ingest path and UI can share one registry.
 */
class ConcordPlaneRegistry {
    private val lock = KmpLock()
    private val planes = HashMap<HexKey, ConcordPlane>()

    /**
     * Registers every joined community's Control Plane address. Idempotent.
     *
     * On a split epoch the address is the entry's held `control_pk` and the wraps
     * still decrypt under the `community_root`-derived read key (CORD-02 §5); on a
     * legacy entry (no `control_pk`) both come from the old single derivation.
     */
    fun registerControlPlanes(entries: List<ConcordCommunityListEntry>) =
        lock.withLock {
            for (e in entries) {
                val cp = ConcordActions.controlPlaneKeysFor(e)
                planes[cp.address] = ConcordPlane(ConcordPlaneKind.CONTROL, e.id, null, cp.address, cp.readKey.conversationKey)
            }
        }

    /**
     * Registers the current Chat Plane address of every channel in a folded community [state] this
     * account can read: a Public Channel's root-derived plane, a Private Channel's held-key plane,
     * and nothing for a Private Channel whose key is not held (CORD-03 §1).
     */
    fun registerChannels(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
    ) = lock.withLock {
        for (channel in state.channels.values) {
            val plane = ConcordActions.currentChannelPlane(entry, channel.channelIdHex, channel.definition.private) ?: continue
            planes[plane.key.publicKeyHex] = ConcordPlane(ConcordPlaneKind.CHANNEL, entry.id, ConcordChannelId(entry.id, plane.channelIdHex), plane.key)
        }
    }

    /** True if [pubKeyHex] is a Concord plane address this account can open. */
    fun isKnownPlane(pubKeyHex: HexKey): Boolean = lock.withLock { pubKeyHex in planes }

    fun planeFor(pubKeyHex: HexKey): ConcordPlane? = lock.withLock { planes[pubKeyHex] }

    /**
     * If [wrap] is a kind-1059 event at a registered plane address, opens it and
     * returns the routed rumor; otherwise null (not Concord, or not ours to read).
     */
    fun route(wrap: Event): RoutedRumor? {
        val plane = planeFor(wrap.pubKey) ?: return null
        val opened = ConcordStreamEnvelope.openOrNull(wrap, plane.address, plane.readConversationKey) ?: return null
        return RoutedRumor(plane, opened)
    }

    fun clear() = lock.withLock { planes.clear() }
}
