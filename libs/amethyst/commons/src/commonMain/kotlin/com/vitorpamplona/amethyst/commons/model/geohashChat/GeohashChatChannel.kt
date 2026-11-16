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
package com.vitorpamplona.amethyst.commons.model.geohashChat

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.concurrent.Volatile

/**
 * A public geohash location chat channel (Bitchat-interoperable), keyed by the
 * bare geohash cell. Unlike an ephemeral relay chat — which lives on one relay —
 * a cell is served by the relays geographically nearest its center, so this
 * channel does not carry a relay in its key; the subscription layer derives the
 * relay set from the geohash.
 */
@Stable
class GeohashChatChannel(
    val geohash: String,
) : Channel() {
    override fun toBestDisplayName() = "#$geohash"

    /**
     * The relays that serve this cell: the ones geographically nearest the cell
     * center (see [GeoRelayDirectory]). Unlike the base implementation — which
     * aggregates the relays a channel's events happened to arrive on — a geohash
     * cell has a well-defined relay set derived from its coordinates, so the
     * subscription layer can reach it even before the first message arrives.
     */
    @Volatile private var cachedRelays: Set<NormalizedRelayUrl>? = null

    @Volatile private var cachedRelaysVersion: Int = -1

    override fun relays(): Set<NormalizedRelayUrl> {
        val dir = GeoRelayDirectory.shared
        val v = dir.version
        // The closest-relays computation is a full sort of the ~370-relay directory; memoize it and
        // recompute only when the directory changes (its version token moves). Two threads racing on a
        // cold cache just both compute the same deterministic result, so no lock is needed.
        cachedRelays?.let { if (cachedRelaysVersion == v) return it }
        val computed = dir.closestRelays(geohash).toSet()
        cachedRelays = computed
        cachedRelaysVersion = v
        return computed
    }

    fun anyNameStartsWith(prefix: String): Boolean = geohash.contains(prefix, true)

    private val placeholderLock = KmpLock()
    private var cachedPlaceholder: Note? = null

    /**
     * A stable empty-row Note so a just-joined cell shows in Messages before its
     * first (ephemeral) message arrives — mirrors the NIP-29 / Marmot group rows.
     */
    fun placeholderNote(): Note =
        placeholderLock.withLock {
            cachedPlaceholder ?: Note(placeholderIdHex(geohash)).apply {
                addGatherer(this@GeohashChatChannel)
                cachedPlaceholder = this
            }
        }

    companion object {
        fun placeholderIdHex(geohash: String): HexKey = "geohash-empty-$geohash"
    }
}
