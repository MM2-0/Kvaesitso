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
package com.vitorpamplona.amethyst.service.nowPlaying

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingPublisher
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingResolver
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingStatusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Publishes what the user is listening to, in Amethyst or in another app, as the logged-in
 * account's NIP-38 music status, following that account's now-playing settings.
 *
 * App-scoped, like the other background watchers in AppModules: the music keeps playing (and
 * other apps keep reporting) after the activity is gone. While the app is in the background the
 * relay pool is paused, so every publish holds [relayServices] open for [RELAY_HOLD_MS], long
 * enough for the event to go out.
 */
class NowPlayingStatusCoordinator(
    private val scope: CoroutineScope,
    private val accountFlow: Flow<Account?>,
    private val relayServices: Flow<*>,
    private val inApp: StateFlow<Map<Int, InAppPlayback>> = InAppPlaybackRegistry.flow,
    private val otherApps: StateFlow<NowPlaying?> = OtherAppsPlaybackRegistry.flow,
    /** Whether the other-apps listener should stay bound: only while the account shares them. */
    private val setOtherAppsWanted: (Boolean) -> Unit = {},
) {
    private var relayHold: Job? = null

    fun start() {
        scope.launch(Dispatchers.IO) {
            accountFlow
                .distinctUntilChanged { a, b -> a?.signer?.pubKey == b?.signer?.pubKey }
                .collectLatest { account ->
                    if (account == null || !account.isWriteable()) {
                        setOtherAppsWanted(false)
                        return@collectLatest
                    }
                    coroutineScope {
                        launch { account.nowPlayingSettings.sharesOtherApps().collect { setOtherAppsWanted(it) } }
                        run(account)
                    }
                }
        }
    }

    private suspend fun run(account: Account) {
        // Every player that is audibly playing, most recently started first: the first one that
        // is a music track or podcast episode wins, so an unmuted feed video started after a song
        // does not hide the song.
        val inAppTracks =
            inApp
                .map { playing -> playing.values.reversed().firstNotNullOfOrNull(::resolve) }
                .flowOn(Dispatchers.IO)

        val publisher =
            NowPlayingPublisher(
                // Relays are woken first: in the background the pool is paused, and the event
                // waits in the outbox until a relay connects.
                publish = { track, expiration ->
                    holdRelays()
                    account.publishNowPlaying(track, expiration)
                },
                clear = {
                    holdRelays()
                    account.clearNowPlaying()
                },
            )

        NowPlayingStatusService(
            settings = account.nowPlayingSettings.flow,
            sources = listOf(inAppTracks, otherApps),
            onOtherAppSeen = { account.nowPlayingSettings.rememberApp(it.id, it.label) },
            publisher = publisher,
        ).run()
    }

    /**
     * What a player's item is as a status, without its end time. Keyed by the item's `nostr:` URI
     * so the NIP-19 parse and the cache lookup run once per item, not once per position update.
     * Only touched from the single flow collecting [inApp].
     */
    private val resolved = HashMap<String, Resolution>()

    private class Resolution(
        val track: NowPlaying?,
    )

    private fun resolve(playback: InAppPlayback): NowPlaying? {
        val cached =
            resolved[playback.callbackUri] ?: run {
                val key = NowPlayingResolver.noteKey(playback.callbackUri)
                val event =
                    if (key != null) {
                        // Not loaded yet: resolve again on the next update instead of caching a miss.
                        LocalCache.getNoteIfExists(key)?.event ?: return null
                    } else {
                        null
                    }
                val track = event?.let { NowPlayingResolver.fromEvent(it, playback.title, playback.artist) }

                if (resolved.size >= MAX_RESOLVED) resolved.clear()
                Resolution(track).also { resolved[playback.callbackUri] = it }
            }

        return cached.track?.copy(endsAt = playback.endsAt)
    }

    private fun holdRelays() {
        relayHold?.cancel()
        relayHold =
            scope.launch(Dispatchers.IO) {
                withTimeoutOrNull(RELAY_HOLD_MS) { relayServices.collect {} }
            }
    }

    companion object {
        private const val RELAY_HOLD_MS = 30_000L
        private const val MAX_RESOLVED = 64
    }
}
