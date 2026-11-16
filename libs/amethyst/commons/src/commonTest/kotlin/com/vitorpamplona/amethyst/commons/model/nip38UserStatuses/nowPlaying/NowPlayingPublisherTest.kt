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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingPublisherTest {
    private val base = 1_700_000_000L

    private class Recorder {
        val published = mutableListOf<Pair<NowPlaying, Long>>()
        var clears = 0
    }

    private fun TestScope.now() = base + testScheduler.currentTime / 1000

    private fun TestScope.start(source: MutableStateFlow<NowPlaying?>): Recorder {
        val recorder = Recorder()
        val publisher =
            NowPlayingPublisher(
                publish = { track, expiration -> recorder.published.add(track to expiration) },
                clear = { recorder.clears++ },
                nowSeconds = { now() },
            )
        backgroundScope.launch { publisher.run(source) }
        runCurrent()
        return recorder
    }

    private fun TestScope.track(
        title: String,
        remainingSeconds: Long? = 180,
    ) = NowPlaying(
        title = title,
        artist = "Artist",
        source = NowPlayingSource.InApp,
        endsAt = remainingSeconds?.let { now() + it },
    )

    @Test
    fun publishesAfterTheStartDelayWithTheTrackEndAsExpiration() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            val song = track("Song")
            source.value = song
            advanceTimeBy(4_000)
            assertEquals(0, recorder.published.size)

            advanceTimeBy(2_000)
            assertEquals(1, recorder.published.size)
            assertEquals(song, recorder.published[0].first)
            assertEquals(song.endsAt, recorder.published[0].second)
        }

    @Test
    fun skippingThroughTracksOnlyPublishesTheOneThatKeepsPlaying() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("One")
            advanceTimeBy(2_000)
            source.value = track("Two")
            advanceTimeBy(2_000)
            source.value = track("Three")
            advanceTimeBy(10_000)

            assertEquals(listOf("Three"), recorder.published.map { it.first.title })
        }

    @Test
    fun aShortPauseNeitherClearsNorRepublishes() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("Song", remainingSeconds = 180)
            advanceTimeBy(6_000)
            assertEquals(1, recorder.published.size)

            source.value = null
            advanceTimeBy(10_000)
            // Resumed 10s later: the end moved by 10s, within the tolerance.
            source.value = track("Song", remainingSeconds = 174)
            advanceTimeBy(60_000)

            assertEquals(0, recorder.clears)
            assertEquals(1, recorder.published.size)
        }

    @Test
    fun stoppingClearsTheStatusAfterTheClearDelay() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("Song")
            advanceTimeBy(6_000)
            source.value = null
            advanceTimeBy(29_000)
            assertEquals(0, recorder.clears)

            advanceTimeBy(2_000)
            assertEquals(1, recorder.clears)
        }

    @Test
    fun aStatusThatAlreadyExpiredIsNotCleared() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("Short", remainingSeconds = 20)
            advanceTimeBy(30_000)
            source.value = null
            advanceTimeBy(120_000)

            assertEquals(1, recorder.published.size)
            assertEquals(0, recorder.clears)
        }

    @Test
    fun seekingRepublishesWithTheNewEnd() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("Song", remainingSeconds = 180)
            advanceTimeBy(6_000)
            val seeked = track("Song", remainingSeconds = 60)
            source.value = seeked
            runCurrent()

            assertEquals(2, recorder.published.size)
            assertEquals(seeked.endsAt, recorder.published[1].second)
        }

    @Test
    fun anUnknownLengthIsRefreshedBeforeItExpires() =
        runTest {
            val source = MutableStateFlow<NowPlaying?>(null)
            val recorder = start(source)

            source.value = track("Radio", remainingSeconds = null)
            advanceTimeBy(6_000)
            assertEquals(1, recorder.published.size)
            assertEquals(now() - 1 + 600, recorder.published[0].second)

            // Refreshed a minute before the 10-minute expiration.
            advanceTimeBy(541_000)
            assertEquals(2, recorder.published.size)
        }

    @Test
    fun pickHonorsSettingsAndTheBlockList() {
        val inApp = NowPlaying("In app", source = NowPlayingSource.InApp)
        val spotify = NowPlaying("Other", source = NowPlayingSource.OtherApp("com.spotify.music", "Spotify"))

        assertNull(pickNowPlaying(NowPlayingSettings(), inApp, spotify))
        assertEquals(inApp, pickNowPlaying(NowPlayingSettings(shareInApp = true, shareOtherApps = true), inApp, spotify))
        assertEquals(spotify, pickNowPlaying(NowPlayingSettings(shareOtherApps = true), inApp, spotify))
        assertEquals(spotify, pickNowPlaying(NowPlayingSettings(shareInApp = true, shareOtherApps = true), null, spotify))
        assertNull(
            pickNowPlaying(
                NowPlayingSettings(shareOtherApps = true, blockedApps = setOf("com.spotify.music")),
                null,
                spotify,
            ),
        )
    }

    @Test
    fun statusTextFollowsTheNipExample() {
        assertEquals("Intergalactic - Beastie Boys", NowPlaying("Intergalactic", "Beastie Boys", NowPlayingSource.InApp).statusText())
        assertEquals("Intergalactic", NowPlaying("Intergalactic", null, NowPlayingSource.InApp).statusText())
    }

    @Test
    fun aPositionTickIsTheSameMomentButASeekIsNot() {
        val song = NowPlaying("Song", "Band", NowPlayingSource.InApp, endsAt = 1_000)

        assertTrue(song.isSameMoment(song.copy(endsAt = 1_004)))
        assertFalse(song.isSameMoment(song.copy(endsAt = 1_060)))
        assertFalse(song.isSameMoment(song.copy(title = "Other")))
        assertFalse(song.isSameMoment(song.copy(source = NowPlayingSource.OtherApp("x", "X"))))
        assertFalse(song.isSameMoment(null))
        assertTrue(song.copy(endsAt = null).isSameMoment(song.copy(endsAt = null)))
    }

    @Test
    fun endsAtAccountsForPositionAndSpeed() {
        assertEquals(1_000L + 60 + 1, NowPlaying.endsAt(1_000, durationMs = 180_000, positionMs = 120_000))
        assertEquals(1_000L + 30 + 1, NowPlaying.endsAt(1_000, durationMs = 180_000, positionMs = 120_000, speed = 2f))
        assertNull(NowPlaying.endsAt(1_000, durationMs = null, positionMs = 0))
        assertNull(NowPlaying.endsAt(1_000, durationMs = 1_000, positionMs = 2_000))
    }
}
