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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.model.nip71Video.captionTracks
import com.vitorpamplona.amethyst.commons.model.nip71Video.mergeCaptionTracks
import com.vitorpamplona.amethyst.commons.model.nip71Video.toCaptionTrack
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Resolves the caption tracks a NIP-71 video advertises, fetching the addressable ones.
 *
 * A `text-track` that names a URL is usable immediately. One that names a
 * `39307:<pubkey>:<d>` coordinate is not, so it gets an [observeNoteEvent] of its own — that both
 * reads the event out of LocalCache and puts it on an EventFinder subscription, so a track the
 * device has never seen is requested from relays and appears when it lands.
 *
 * The same track is routinely advertised both ways at once (divine.video publishes the Blossom URL
 * and its wrapping event side by side), so the two are merged by URL: the player is handed one
 * configuration per distinct file, never a duplicate pair it would offer twice in its track menu.
 */
@Composable
fun rememberCaptionTracks(
    videoEvent: VideoEvent,
    accountViewModel: AccountViewModel,
): ImmutableList<CaptionTrack> {
    val refs = remember(videoEvent) { videoEvent.captionTracks() }

    // No early return: the pending list changes size when a recycled feed slot switches videos,
    // and `key` is what lets Compose add and drop those child subscriptions in place.
    val resolved =
        refs.pending.map { pending ->
            key(pending.address.toValue()) {
                val note = remember(pending.address) { LocalCache.getOrCreateAddressableNote(pending.address) }
                val trackEvent by observeNoteEvent<TextTrackEvent>(note, accountViewModel)
                trackEvent?.toCaptionTrack(pending.tag)
            }
        }

    return remember(refs, resolved) {
        if (refs.direct.isEmpty() && resolved.all { it == null }) {
            persistentListOf()
        } else {
            mergeCaptionTracks(refs.direct, resolved.filterNotNull()).toImmutableList()
        }
    }
}
