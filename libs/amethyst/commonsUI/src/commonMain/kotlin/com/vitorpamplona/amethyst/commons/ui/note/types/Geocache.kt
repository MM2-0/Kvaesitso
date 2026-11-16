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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_nearby
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.note.FoundLogProof
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheCard
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheFoundLogCard
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocationPreviewMap
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Entry for a NIP-CC geocache listing (kind 37516): decodes the [Note] and renders the shared
 * commons [GeocacheCard], supplying the native map hero as its slot.
 *
 * "Claimed" is read off the listing's own `F` tag rather than computed from the cache's found
 * logs. NIP-CC makes `F` the authoritative answer once the owner publishes it ("clients MUST
 * attribute the exclusive claim to the pubkey in the `F` tag"), and it travels inside the event,
 * so a card can state it without holding the logs. The provisional, timestamp-ordered winner
 * needs every verified log for the cache and belongs on a screen that subscribes to them —
 * deriving it from whatever replies happen to be in memory would render a claim that flickers.
 */
@Composable
fun RenderGeocache(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? GeocacheListingEvent ?: return

    val claimed = remember(noteEvent) { noteEvent.firstToFindWinner() != null }
    val distance = distanceToCache(noteEvent, accountViewModel)

    GeocacheCard(noteEvent, (LocalMediaCapture.current != null || accountViewModel.settings.showImages()), claimed, distance) { latitude, longitude, pinColor, pinEmoji, pinAlpha, aspectRatio ->
        LocationPreviewMap(
            latitude = latitude,
            longitude = longitude,
            aspectRatio = aspectRatio,
            pinColor = pinColor,
            pinEmoji = pinEmoji,
            pinAlpha = pinAlpha,
        )
    }
}

/**
 * How far the reader is from the cache, or null when the app does not already know where they
 * are.
 *
 * Deliberately reads `.value` instead of collecting. The location flow is
 * `SharingStarted.WhileSubscribed`, so collecting it from a feed card would switch the GPS on
 * for anyone who merely scrolled past a geocache — a real battery and privacy cost, paid
 * silently, for a line of text. Its initial value is the last fix the app cached, so reading it
 * costs nothing and yields a distance whenever something else (the Around Me feed, the location
 * picker) has already asked. Where nothing has, the card simply shows no distance.
 *
 * The trade is that this does not re-render as the reader walks. A card in a scrolling feed is
 * not a compass; the detail screen is the place to track a fix.
 */
@Composable
fun distanceToCache(
    noteEvent: GeocacheListingEvent,
    accountViewModel: AccountViewModel,
): String? {
    val here = accountViewModel.account.geolocationFlow().value as? LocationResult.Success ?: return null

    val nearby = stringRes(Res.string.geocache_nearby)

    return remember(noteEvent, here, nearby) {
        val cache = noteEvent.location()?.toGeoHash() ?: return@remember null
        GeoRelayDirectory
            .haversineKm(
                here.geoHash.centerLat,
                here.geoHash.centerLon,
                cache.centerLat,
                cache.centerLon,
            ).let { formatDistance(it, nearby) }
    }
}

/**
 * A distance formatted no finer than its source can support.
 *
 * The reader's position comes from `LocationState.geohashStateFlow`, which is a **5-character
 * geohash** — a 4.89km × 4.89km cell — so the centroid this measures from can be ~3.5km from
 * where the reader is standing. Printing metres off that would be fiction: someone standing at
 * the cache would read "3 km", and a cache 2km away could read "120 m". Anything inside one
 * cell is therefore [NEARBY] rather than a number, and everything else is a rounded kilometre
 * with a `~`.
 *
 * Coarse twice over, in fact: Amethyst declares only `ACCESS_COARSE_LOCATION`, so Android fuzzes
 * each fix before the geohash is even computed.
 */
private fun formatDistance(
    km: Double,
    nearby: String,
): String = if (km < CELL_KM) nearby else "~${km.roundToInt()} km"

/** Width of the geohash cell the reader's position is quantised to, in kilometres. */
private const val CELL_KM = 4.89

/**
 * Entry for a NIP-CC found log (kind 7516).
 *
 * The proof badge is decided here rather than in the card because deciding it needs the cache
 * listing: a `verification` tag is only a string until it has been checked against the listing's
 * verification key, this log's own author, and the cache the log claims. So the listing is
 * loaded and observed — which also asks the relays for it — and until it arrives the answer is
 * [FoundLogProof.UNKNOWN], which the card renders as no badge at all rather than an optimistic
 * one.
 *
 * Two things are deliberately kept off the composition thread's critical path:
 *
 * - The cheap gate is [GeocacheFoundLogEvent.hasVerificationAttached], which reads tag names.
 *   Parsing the embedded 7517 means running the payload through the JSON parser and the event
 *   factory, and a feed must not pay for that on every recomposition of every log.
 * - Validating it costs a SHA-256 and a secp256k1 verification, which is not a composition-thread
 *   amount of work, so it runs in [produceState] on [Dispatchers.Default].
 */
@Composable
fun RenderGeocacheFoundLog(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? GeocacheFoundLogEvent ?: return
    val address = remember(noteEvent) { noteEvent.geocache() }
    val hasProof = remember(noteEvent) { noteEvent.hasVerificationAttached() }
    val showImages = (LocalMediaCapture.current != null || accountViewModel.settings.showImages())

    if (address == null) {
        GeocacheFoundLogCard(noteEvent, showImages, FoundLogProof.NONE)
        return
    }

    // Loaded even for an unverified log: the cache's name is what tells a reader in a feed what
    // was actually found, and "Found it!" on its own says nothing.
    LoadAddressableNote(address) { cacheNote ->
        if (cacheNote == null) {
            GeocacheFoundLogCard(noteEvent, showImages, if (hasProof) FoundLogProof.UNKNOWN else FoundLogProof.NONE)
        } else {
            // Read through the note rather than observeNoteEvent<GeocacheListingEvent>: that
            // helper's cast is erased, so it hands back whatever the address resolved to and the
            // ClassCastException lands at this read site. The address comes from the log's `a`
            // tag — attacker-controlled — so the cast has to be the checked kind.
            val noteState by observeNote(cacheNote, accountViewModel)
            val listing = noteState.note.event as? GeocacheListingEvent

            val proof by
                produceState(if (hasProof) FoundLogProof.UNKNOWN else FoundLogProof.NONE, noteEvent, listing, hasProof) {
                    val cache = listing
                    value =
                        when {
                            !hasProof -> FoundLogProof.NONE
                            cache == null -> FoundLogProof.UNKNOWN
                            else ->
                                withContext(Dispatchers.Default) {
                                    if (GeocacheVerificationValidator.isValid(noteEvent, cache)) {
                                        FoundLogProof.VALID
                                    } else {
                                        FoundLogProof.INVALID
                                    }
                                }
                        }
                }

            GeocacheFoundLogCard(noteEvent, showImages, proof, listing?.cacheName())
        }
    }
}
