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
package com.vitorpamplona.amethyst.commons.wot.network

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.passesMinRank

/**
 * Everything a Web of Trust decision depends on, frozen: the network, the cards seen since its
 * sync, the minimum score, the user and who they follow. The single rule ([explain]) lives here,
 * and every consumer (DM tabs, notifications, replies, chats, profiles, `amy trust check`) asks a
 * snapshot, so none can forget an input: a consumer keyed on the snapshot re-runs whenever an
 * answer could change.
 *
 * Snapshots are published by `TrustNetworkState.verdicts` only when an answer can change, not
 * when a rank moves without crossing the minimum; read live ranks from `TrustNetworkState`.
 */
@Immutable
class TrustVerdicts(
    val network: TrustNetwork?,
    private val overlay: Map<HexKey, TrustOverlayCard>,
    val minScore: Int,
    private val me: HexKey,
    private val follows: Set<HexKey>,
    /** Bumps when a card seen between syncs moves someone in or out. */
    private val revision: Int = 0,
) {
    /**
     * Filtering applies: the user has a provider and its complete set is loaded (see
     * `TrustNetworkState.isActive`). While false every answer is "no network" and nothing is
     * hidden, collapsed or moved.
     */
    val isActive: Boolean get() = network != null

    /** The provider's rank for [pubkey]: a newer card seen since the sync, else the index. */
    fun rankOf(pubkey: HexKey): Int? = rankIn(network, overlay, pubkey)

    /** True when the provider ranks [pubkey] at the minimum score or above. */
    fun passes(pubkey: HexKey): Boolean = passesMinRank(rankOf(pubkey), minScore)

    /** Why [pubkey] is or is not in the user's network. */
    fun explain(pubkey: HexKey): TrustVerdict {
        if (network == null) return TrustVerdict.NO_NETWORK
        if (pubkey == me) return TrustVerdict.SELF
        if (pubkey in follows) return TrustVerdict.FOLLOW
        val rank = rankOf(pubkey) ?: return TrustVerdict.NOT_IN_NETWORK
        return if (rank >= minScore) TrustVerdict.TRUSTED else TrustVerdict.BELOW_MIN_SCORE
    }

    /** True only when a network is active and [pubkey] is not in it. */
    fun isOutside(pubkey: HexKey): Boolean = explain(pubkey).isKnown == false

    /**
     * Every answer is the same as [other]'s: same index, cards, minimum, user and follows. With
     * no network on either side every answer is "no network", whatever the follows.
     */
    fun sameAnswersAs(other: TrustVerdicts): Boolean =
        (network == null && other.network == null) ||
            (
                network?.index === other.network?.index &&
                    revision == other.revision &&
                    minScore == other.minScore &&
                    me == other.me &&
                    follows === other.follows
            )

    companion object {
        fun inactive(me: HexKey) = TrustVerdicts(null, emptyMap(), 0, me, emptySet())
    }
}

/** [pubkey]'s rank in [network], where a card in [overlay] (newer than the index) wins. */
internal fun rankIn(
    network: TrustNetwork?,
    overlay: Map<HexKey, TrustOverlayCard>,
    pubkey: HexKey,
): Int? {
    val loaded = network ?: return null
    val card = overlay[pubkey] ?: return loaded.index.rankOf(pubkey)
    return card.rank
}
