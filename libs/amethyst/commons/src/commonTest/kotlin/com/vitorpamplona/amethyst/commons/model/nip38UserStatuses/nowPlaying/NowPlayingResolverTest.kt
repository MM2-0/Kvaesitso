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

import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingResolverTest {
    private val id = "a".repeat(64)
    private val pubKey = "b".repeat(64)
    private val sig = "c".repeat(128)

    @Test
    fun musicTrackPointsAtItsAddress() {
        val event =
            MusicTrackEvent(
                id,
                pubKey,
                1L,
                arrayOf(arrayOf("d", "song"), arrayOf("title", "Song"), arrayOf("artist", "Band")),
                "",
                sig,
            )

        val track = NowPlayingResolver.fromEvent(event, fallbackArtist = "Uploader", endsAt = 10L)!!

        assertEquals("Song - Band", track.statusText())
        assertEquals("${MusicTrackEvent.KIND}:$pubKey:song", track.addressId)
        assertNull(track.eventId)
        assertEquals(10L, track.endsAt)
        assertEquals(NowPlayingSource.InApp, track.source)
    }

    @Test
    fun podcastEpisodeUsesTheShowAsArtistAndPointsAtTheEvent() {
        val event = PodcastEpisodeEvent(id, pubKey, 1L, arrayOf(arrayOf("title", "Episode 1")), "", sig)

        val track = NowPlayingResolver.fromEvent(event, fallbackArtist = "The Show")!!

        assertEquals("Episode 1 - The Show", track.statusText())
        assertEquals(id, track.eventId)
        assertNull(track.addressId)
    }

    @Test
    fun otherEventsAreNotShared() {
        val event = TextNoteEvent(id, pubKey, 1L, emptyArray(), "a video", sig)

        assertNull(NowPlayingResolver.fromEvent(event, fallbackTitle = "video", fallbackArtist = "someone"))
    }
}
