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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying

import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

/**
 * Turns the Nostr event behind an in-app player into a [NowPlaying], or null when the event is
 * not something to share as a listening status.
 *
 * Only music tracks (kind 36787), audio tracks (kind 31337) and podcast episodes (both drafts)
 * qualify. Everything else the app plays (feed videos, voice messages, live streams) returns null,
 * so autoplaying a clip never announces itself as "listening to".
 *
 * [fallbackTitle] and [fallbackArtist] are what the player shows (the event's title and the
 * author's display name) and fill the gaps an event leaves.
 */
object NowPlayingResolver {
    /**
     * Resolves the `nostr:` URI the in-app player carries (`Note.toNostrUri()`) to its event with
     * [findEvent] (given an event id or an `kind:pubkey:d` address) and then calls [fromEvent].
     */
    fun fromNostrUri(
        uri: String,
        findEvent: (key: String) -> Event?,
        fallbackTitle: String? = null,
        fallbackArtist: String? = null,
        endsAt: Long? = null,
    ): NowPlaying? {
        val key = noteKey(uri) ?: return null
        val event = findEvent(key) ?: return null
        return fromEvent(event, fallbackTitle, fallbackArtist, endsAt)
    }

    /** The cache key (event id, or `kind:pubkey:d` address) a `nostr:` note URI points at. */
    fun noteKey(uri: String): String? =
        when (val entity = Nip19Parser.uriToRoute(uri)?.entity) {
            is NEvent -> entity.hex
            is NNote -> entity.hex
            is NAddress -> entity.aTag()
            else -> null
        }

    fun fromEvent(
        event: Event,
        fallbackTitle: String? = null,
        fallbackArtist: String? = null,
        endsAt: Long? = null,
    ): NowPlaying? {
        val title: String?
        val artist: String?

        when (event) {
            is MusicTrackEvent -> {
                title = event.title()
                artist = event.artist() ?: fallbackArtist
            }

            is AudioTrackEvent -> {
                title = event.subject()
                artist = fallbackArtist
            }

            is PodcastEpisode -> {
                title = event.episodeTitle()
                artist = fallbackArtist
            }

            else -> {
                return null
            }
        }

        val bestTitle = title?.ifBlank { null } ?: fallbackTitle?.ifBlank { null } ?: return null

        return NowPlaying(
            title = bestTitle,
            artist = artist?.ifBlank { null },
            source = NowPlayingSource.InApp,
            endsAt = endsAt,
            addressId = (event as? AddressableEvent)?.address()?.toValue(),
            eventId = if (event is AddressableEvent) null else event.id,
        )
    }
}
