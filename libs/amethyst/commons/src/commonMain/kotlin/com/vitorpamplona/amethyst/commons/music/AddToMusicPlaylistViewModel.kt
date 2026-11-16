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
package com.vitorpamplona.amethyst.commons.music

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lightweight projection for the picker rows. */
@Immutable
data class OwnedPlaylistSummary(
    val address: Address,
    val title: String,
    val trackCount: Int,
    val containsTrack: Boolean,
)

/**
 * Loads the current user's MusicPlaylistEvents (kind 34139) and exposes add/remove operations
 * that re-sign and re-broadcast the addressable with the same `d` tag — clients keying off the
 * address see the new ordered track list automatically.
 */
class AddToMusicPlaylistViewModel : ViewModel() {
    private lateinit var account: Account
    private var trackAddress: Address? = null

    val ownedPlaylists = mutableStateOf<List<OwnedPlaylistSummary>>(emptyList())
    val isWorking = mutableStateOf(false)

    private var liveScanJob: Job? = null

    /**
     * Serializes concurrent toggle/create calls. `isWorking` alone is a UI gate, not a
     * concurrency primitive — quick double taps on different rows would otherwise both pass
     * the early-return check, both read the same `existing` event, and the loser's broadcast
     * would replace the winner's (last-write-wins by `createdAt`).
     */
    private val mutationLock = Mutex()

    fun init(
        account: Account,
        trackAddressTag: String,
    ) {
        // Always refresh the parsed track address. The previous guard (`if account already
        // set, return`) silently kept the FIRST track this VM ever saw — any later call with
        // a different track would route toggle() at the stale address, adding/removing the
        // wrong track from playlists. ViewModels survive process recreation and can also be
        // reused by navigation in less common shapes, so the input has to drive the state.
        val parsed = AddressSerializer.parse(trackAddressTag)
        if (parsed == trackAddress && ::account.isInitialized && this.account == account) {
            // Same inputs — nothing to do. The live-scan job stays running.
            return
        }
        this.account = account
        this.trackAddress = parsed

        // (Re)start the scan against the new track so each playlist's `containsTrack` flag
        // reflects the *current* trackAddress instead of the previous one.
        liveScanJob?.cancel()
        liveScanJob =
            viewModelScope.launch(Dispatchers.IO) {
                rescan()
                LocalCache.live.newEventBundles.collect { bundle ->
                    if (bundle.any { it.event?.kind == MusicPlaylistEvent.KIND }) {
                        rescan()
                    }
                }
            }
    }

    override fun onCleared() {
        liveScanJob?.cancel()
    }

    private suspend fun rescan() {
        val mePubKey = account.userProfile().pubkeyHex
        val target = trackAddress
        val results =
            LocalCache.addressables
                .filterIntoSet(MusicPlaylistEvent.KIND, mePubKey) { _, note ->
                    note.event is MusicPlaylistEvent
                }.mapNotNull { note ->
                    val e = note.event as? MusicPlaylistEvent ?: return@mapNotNull null
                    val tracks = e.trackAddresses()
                    OwnedPlaylistSummary(
                        address = e.address(),
                        title = e.title().orEmpty(),
                        trackCount = tracks.size,
                        containsTrack = target != null && tracks.any { it == target },
                    )
                }.sortedByDescending { it.containsTrack }
        // Hop back to the main thread to publish the new list — Compose State writes go
        // through Snapshot.apply, and doing them off-main is the anti-pattern flagged in
        // prior reviews of this codebase even though Snapshot makes it not-yet-broken.
        withContext(Dispatchers.Main.immediate) {
            ownedPlaylists.value = results
        }
    }

    /**
     * Toggle membership: if the playlist already contains the track, drop it; otherwise append
     * to the end. Uses [MusicPlaylistEvent.addTrack] / [MusicPlaylistEvent.removeTrack] so every
     * non-`a` tag the original event carried (custom `t` tags, description, image, etc) is
     * preserved. Returns false if no track was supplied or the playlist isn't in cache.
     */
    suspend fun toggle(playlistAddress: Address): Boolean {
        val targetTrack = trackAddress ?: return false
        return mutationLock.withLock {
            if (isWorking.value) return@withLock false
            setWorking(true)
            try {
                val existing = LocalCache.addressables.get(playlistAddress)?.event as? MusicPlaylistEvent ?: return@withLock false

                val template =
                    if (existing.trackAddresses().any { it == targetTrack }) {
                        MusicPlaylistEvent.removeTrack(existing, targetTrack)
                    } else {
                        MusicPlaylistEvent.addTrack(existing, targetTrack)
                    }

                account.signAndComputeBroadcast(template)
                rescan()
                true
            } finally {
                setWorking(false)
            }
        }
    }

    /** Create a brand-new playlist containing this track. Returns the new playlist's address. */
    suspend fun createWithTrack(title: String): Address? {
        val targetTrack = trackAddress ?: return null
        if (title.isBlank()) return null
        return mutationLock.withLock {
            if (isWorking.value) return@withLock null
            setWorking(true)
            try {
                val event =
                    account.signAndComputeBroadcast(
                        MusicPlaylistEvent.build(
                            title = title.trim(),
                            tracks = listOf(targetTrack),
                        ),
                    )
                rescan()
                event.address()
            } finally {
                setWorking(false)
            }
        }
    }

    /**
     * Publish the [isWorking] flag on the main thread. See the comment in [rescan]: this
     * coroutine is launched on [Dispatchers.IO] (via `launchSigner`), and Compose State
     * writes belong on the main dispatcher even when Snapshot would technically tolerate
     * them off-main.
     */
    private suspend fun setWorking(working: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            isWorking.value = working
        }
    }
}
