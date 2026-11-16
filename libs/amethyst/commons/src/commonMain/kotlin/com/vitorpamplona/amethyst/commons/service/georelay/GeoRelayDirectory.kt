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
package com.vitorpamplona.amethyst.commons.service.georelay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlin.concurrent.Volatile
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps a geohash cell to the Nostr relays geographically closest to its center,
 * so that a client publishing to (and subscribing from) a location channel lands
 * on the same relays every other client of that cell uses.
 *
 * This mirrors Bitchat's `GeoRelayDirectory`: the relay list comes from the
 * public, MIT-licensed directory at [CSV_URL] (columns `Relay URL,Latitude,
 * Longitude`) which both clients load, and selection is the [DEFAULT_COUNT]
 * relays nearest the cell center by great-circle distance, ties broken by host
 * name so every device picks the same set. Because the CSV can change over time,
 * clients should refresh it at runtime (see the platform loader); [FALLBACK] is a
 * small built-in list so first-run/offline still has somewhere to rendezvous.
 *
 * Attribution: relay coordinates are from
 * https://github.com/permissionlesstech/georelays (MIT).
 */
class GeoRelayDirectory(
    initial: List<GeoRelay> = FALLBACK,
) {
    // Read from subscription/UI threads, replaced by the CSV-loader coroutine — @Volatile so the
    // refresh is actually visible to readers (otherwise they can keep seeing FALLBACK forever).
    @Volatile private var relays: List<GeoRelay> = initial

    // Bumped whenever the directory contents change, so cheap consumers (e.g. GeohashChatChannel) can
    // memoize a derived relay set and recompute only when this token moves.
    @Volatile var version: Int = 0
        private set

    /** Replace the directory contents (e.g. after a successful CSV refresh). No-op on empty input. */
    fun setRelays(list: List<GeoRelay>) {
        if (list.isNotEmpty()) {
            relays = list
            version++
        }
    }

    fun snapshot(): List<GeoRelay> = relays

    val size: Int get() = relays.size

    /**
     * The [count] relays closest to the center of [geohash]. Returns an empty
     * list if [geohash] is not a valid geohash or the directory is empty.
     */
    fun closestRelays(
        geohash: String,
        count: Int = DEFAULT_COUNT,
    ): List<NormalizedRelayUrl> {
        val center = GeoHash.decode(geohash) ?: return emptyList()
        return closest(center.centerLat, center.centerLon, relays, count)
    }

    companion object {
        const val DEFAULT_COUNT = 5

        const val CSV_URL = "https://raw.githubusercontent.com/permissionlesstech/georelays/refs/heads/main/nostr_relays.csv"

        private const val EARTH_RADIUS_KM = 6371.0

        /**
         * Pure selection: the [count] nearest relays to ([lat], [lon]) by
         * great-circle distance, ties broken by [GeoRelay.host] ascending for a
         * deterministic, cross-client-stable set.
         */
        fun closest(
            lat: Double,
            lon: Double,
            relays: List<GeoRelay>,
            count: Int = DEFAULT_COUNT,
        ): List<NormalizedRelayUrl> =
            relays
                // Compute the great-circle distance once per relay; folding it into the comparator
                // selector instead would re-run the trig on every comparison (O(n log n) times).
                .map { it to haversineKm(lat, lon, it.latitude, it.longitude) }
                .sortedWith(compareBy({ it.second }, { it.first.host }))
                .map { it.first.relay }
                // The directory lists some hosts both bare and with an explicit :443 (the
                // wss default) — those collapse to one relay, so drop the duplicate before
                // taking count and never spend two of the N slots on the same endpoint.
                .distinct()
                .take(count)

        /** Great-circle distance in kilometers between two lat/lon points. */
        fun haversineKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val dLat = (lat2 - lat1).toRadians()
            val dLon = (lon2 - lon1).toRadians()
            val a =
                sin(dLat / 2) * sin(dLat / 2) +
                    cos(lat1.toRadians()) * cos(lat2.toRadians()) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
        }

        private fun Double.toRadians() = this / 180.0 * kotlin.math.PI

        /**
         * Parses the georelays CSV (`Relay URL,Latitude,Longitude`). The relay
         * column is a bare host (optionally with a port); it is turned into a
         * `wss://` URL. Rows with an unparseable relay or coordinates are skipped,
         * as is the header row.
         */
        fun parseCsv(text: String): List<GeoRelay> =
            text
                .lineSequence()
                .mapNotNull { parseRow(it) }
                .toList()

        private fun parseRow(line: String): GeoRelay? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val cols = trimmed.split(',')
            if (cols.size < 3) return null
            val host = cols[0].trim()
            if (host.isEmpty() || host.equals("Relay URL", ignoreCase = true)) return null
            val lat = cols[1].trim().toDoubleOrNull() ?: return null
            val lon = cols[2].trim().toDoubleOrNull() ?: return null
            // wss defaults to 443, so a bare host and the same host with :443 are the
            // same endpoint — canonicalize to the bare form so they dedup.
            val canonicalHost = if ("://" in host) host else host.removeSuffix(":443")
            val relay = RelayUrlNormalizer.normalizeOrNull(if ("://" in canonicalHost) canonicalHost else "wss://$canonicalHost") ?: return null
            return GeoRelay(host, relay, lat, lon)
        }

        private fun geoRelayOf(
            host: String,
            lat: Double,
            lon: Double,
        ): GeoRelay? {
            val relay = RelayUrlNormalizer.normalizeOrNull("wss://$host") ?: return null
            return GeoRelay(host, relay, lat, lon)
        }

        /**
         * A small, geographically-spread built-in directory used until the live
         * CSV is fetched. Not exhaustive — the runtime refresh replaces it with
         * the full ~370-relay directory that Bitchat also uses.
         */
        val FALLBACK: List<GeoRelay> =
            listOfNotNull(
                geoRelayOf("relay.damus.io", 37.7775, -122.4163),
                geoRelayOf("nos.lol", 39.0438, -77.4874),
                geoRelayOf("relay.primal.net", 40.7128, -74.0060),
                geoRelayOf("offchain.pub", 52.3676, 4.9041),
                geoRelayOf("relay.lab.rytswd.com", 49.4543, 11.0746),
                geoRelayOf("relay.binaryrobot.com", 43.6532, -79.3832),
                geoRelayOf("nostr-2.21crypto.ch", 47.5356, 8.73209),
                geoRelayOf("spookstr2.nostr1.com", 40.7057, -74.0136),
                geoRelayOf("freelay.sovbit.host", 60.1699, 24.9384),
                geoRelayOf("nostr-rs-relay-qj1h.onrender.com", 37.7775, -122.397),
                geoRelayOf("relay.angor.io", 48.1046, 11.6002),
                geoRelayOf("nostr-01.yakihonne.com", 1.32123, 103.695),
                geoRelayOf("relay.guggero.org", 46.5971, 9.59652),
                geoRelayOf("relay.nostr.band", 50.1109, 8.6821),
                geoRelayOf("nostr.wine", 45.5152, -122.6784),
            )

        /**
         * Process-wide directory shared by everything that routes geohash chat
         * traffic — the [GeohashChatChannel][com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel]
         * relay override, the joined-cell subscription, the chat screen. Seeded
         * with [FALLBACK]; a platform loader replaces it with the live CSV at
         * runtime via [setRelays].
         */
        val shared = GeoRelayDirectory()
    }
}
