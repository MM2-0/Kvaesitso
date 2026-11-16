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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.music_playlist_send_failed_title
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Composer for a kind-34139 Music Playlist. Mirrors the [NewMusicTrackViewModel] flow: the user
 * picks a cover image up front (Save uploads it through Blossom/NIP-96, then publishes the
 * addressable event with the returned URL) and fills the metadata fields.
 *
 * In edit mode (`editDTag` set AND the event resolves from LocalCache), publishes via
 * [MusicPlaylistEvent.edit] so the playlist's track list (`a` tags) and every other tag the
 * composer doesn't surface are preserved across save. When `editDTag` is set but the event isn't
 * in cache, falls back to create-mode so the user isn't trapped on a Delete button that wouldn't
 * do anything.
 */
class NewMusicPlaylistViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")

    /** Short description, written into the `description` tag. */
    val description = mutableStateOf("")

    /** Long-form notes, written into `event.content` (the JSON `content` field). */
    val notes = mutableStateOf("")

    val coverUrl = mutableStateOf("")
    val isPrivate = mutableStateOf(false)
    val isCollaborative = mutableStateOf(false)

    /**
     * Working, ordered list of track addresses the editor mutates (reorder / remove). Seeded from
     * the loaded event in edit mode; published in this order on save. Adding *new* tracks still
     * happens through the per-song "Add to playlist" sheet — this screen only manages the tracks
     * already in the playlist.
     */
    val tracks = mutableStateOf<List<Address>>(emptyList())

    /**
     * Single in-flight flag covering both the cover upload and the subsequent publish. Drives the
     * Send button's spinner, gates double-tap, and overlays the picker with a progress indicator.
     */
    val isSending = mutableStateOf(false)

    /**
     * One-shot success channel. Emits exactly once when an upload+publish round succeeds; the
     * screen collects it and pops back. See [NewMusicTrackViewModel] for why this is a flow rather
     * than a callback (the work survives screen dismissal on [AccountViewModel.viewModelScope]).
     */
    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    val coverMedia = mutableStateOf<MultiOrchestrator?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()

    val selectedServer = mutableStateOf<ServerName?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED — matches Badge's slider semantics.
    val mediaQualitySlider = mutableStateOf(CompressorQuality.defaultSliderPosition)
    val stripMetadata = mutableStateOf(true)

    /** Stable d-tag for the addressable: null = create-new, non-null = edit-existing. */
    private var dTag: String? = null

    /** The loaded event in edit mode; needed for `edit()` + NIP-09 deletion. */
    private var loadedEvent: MusicPlaylistEvent? = null

    /** Only `true` once an existing event has been resolved from cache. */
    val isEditing: Boolean
        get() = loadedEvent != null

    fun init(
        accountViewModel: AccountViewModel,
        editDTag: String?,
    ) {
        if (::account.isInitialized) return // idempotent across recompositions
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload

        if (editDTag != null) {
            val existingAddress = Address(MusicPlaylistEvent.KIND, account.userProfile().pubkeyHex, editDTag)
            val existingNote = LocalCache.addressables.get(existingAddress)
            (existingNote?.event as? MusicPlaylistEvent)?.let { existing ->
                dTag = editDTag
                loadedEvent = existing
                title.value = existing.title().orEmpty()
                description.value = existing.description().orEmpty()
                notes.value = existing.content
                coverUrl.value = existing.image().orEmpty()
                isPrivate.value = existing.isPrivate()
                isCollaborative.value = existing.isCollaborative()
                tracks.value = existing.trackAddresses()
            }
            // If the lookup fails, dTag stays null and the screen renders as create-mode.
        }
    }

    fun setPickedCover(uris: ImmutableList<SelectedMedia>) {
        coverMedia.value = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun clearPickedCover() {
        coverMedia.value = null
        // Also drop any previously-published cover so "remove" sticks when editing.
        coverUrl.value = ""
    }

    /** Swap the track at [index] with the one above it. No-op at the top or out of bounds. */
    fun moveTrackUp(index: Int) {
        val current = tracks.value
        if (index <= 0 || index >= current.size) return
        tracks.value =
            current.toMutableList().apply {
                add(index - 1, removeAt(index))
            }
    }

    /** Swap the track at [index] with the one below it. No-op at the bottom or out of bounds. */
    fun moveTrackDown(index: Int) {
        val current = tracks.value
        if (index < 0 || index >= current.size - 1) return
        tracks.value =
            current.toMutableList().apply {
                add(index + 1, removeAt(index))
            }
    }

    /** Drop the track at [index] from the working list. No-op when out of bounds. */
    fun removeTrackAt(index: Int) {
        val current = tracks.value
        if (index < 0 || index >= current.size) return
        tracks.value = current.toMutableList().apply { removeAt(index) }
    }

    /** A title is the only hard requirement; everything else is optional. */
    fun isValid(): Boolean = title.value.isNotBlank()

    /**
     * Upload the picked cover (if any), then sign and broadcast the event. The whole operation
     * runs on [AccountViewModel.viewModelScope] via `launchSigner`, so it keeps running even if
     * the user leaves the screen — they get a toast notification when it finishes either way.
     */
    fun saveAndPublish(accountViewModel: AccountViewModel) {
        if (isSending.value) return // double-tap guard
        if (!isValid()) return

        val acc = account
        val server = selectedServer.value
        if (server == null && coverMedia.value != null) {
            accountViewModel.toastManager.toast(
                Res.string.upload_no_server_title,
                Res.string.upload_no_server_message,
            )
            return
        }

        // Snapshot every input the upload needs into immutable locals BEFORE launching, so user
        // edits to the form after pressing Send don't poison the in-flight publish.
        val snapshot =
            SendSnapshot(
                title = title.value.trim(),
                description = description.value.trim().ifBlank { null },
                notes = notes.value,
                tracks = tracks.value,
                isPrivate = isPrivate.value,
                isCollaborative = isCollaborative.value,
                coverOrchestrator = coverMedia.value,
                existingCoverUrl = coverUrl.value.trim().ifBlank { null },
                server = server,
                quality = CompressorQuality.fromSlider(mediaQualitySlider.value),
                stripMetadata = stripMetadata.value,
                loadedEvent = loadedEvent,
                uploader = accountViewModel.mediaUploader,
            )

        isSending.value = true

        accountViewModel.launchSigner {
            try {
                val newCoverUrl =
                    snapshot.coverOrchestrator?.let { runCoverUpload(it, snapshot) }
                val finalCoverUrl = newCoverUrl ?: snapshot.existingCoverUrl

                publishEvent(snapshot, finalCoverUrl)

                // Remember the server + strip-metadata choice so the user doesn't have to re-pick
                // them on the next playlist or track.
                snapshot.server?.let { acc.settings.changeDefaultFileServer(it) }
                acc.settings.changeStripLocationOnUpload(snapshot.stripMetadata)

                withContext(Dispatchers.Main.immediate) {
                    coverMedia.value = null
                    if (newCoverUrl != null) coverUrl.value = newCoverUrl
                }
                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast(
                    Res.string.music_playlist_send_failed_title,
                    t.message ?: t::class.simpleName ?: "",
                    t,
                )
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }

    private data class SendSnapshot(
        val title: String,
        val description: String?,
        val notes: String,
        val tracks: List<Address>,
        val isPrivate: Boolean,
        val isCollaborative: Boolean,
        val coverOrchestrator: MultiOrchestrator?,
        val existingCoverUrl: String?,
        val server: ServerName?,
        val quality: CompressorQuality,
        val stripMetadata: Boolean,
        val loadedEvent: MusicPlaylistEvent?,
        val uploader: MediaUploader,
    )

    private suspend fun runCoverUpload(
        orch: MultiOrchestrator,
        snapshot: SendSnapshot,
    ): String {
        val server = snapshot.server ?: throw UploadException("No upload server selected.")
        val res =
            orch.upload(
                alt = snapshot.title.ifBlank { null },
                contentWarningReason = null,
                mediaQuality = snapshot.quality,
                server = server,
                account = account,
                uploader = snapshot.uploader,
                useH265 = false,
                stripMetadata = snapshot.stripMetadata,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            )
        if (!res.allGood) {
            throw UploadException(formatUploadErrors(res.errors))
        }
        return firstUploadedUrl(res.successful)
            ?: throw UploadException("Server didn't return a URL for the uploaded cover.")
    }

    private class UploadException(
        details: String,
    ) : Exception("Cover upload failed: $details")

    private fun firstUploadedUrl(successful: List<UploadingState.Finished>): String? =
        successful
            .firstNotNullOfOrNull { it.result as? UploadOrchestrator.OrchestratorResult.ServerResult }
            ?.url

    private suspend fun formatUploadErrors(errors: List<UploadingState.Error>): String =
        errors
            .map { loadStringRes(it.errorResource, *it.params) }
            .distinct()
            .joinToString(".\n")

    private suspend fun publishEvent(
        snapshot: SendSnapshot,
        coverUrl: String?,
    ) {
        val existing = snapshot.loadedEvent
        val template =
            if (existing != null) {
                MusicPlaylistEvent.edit(
                    earlierVersion = existing,
                    title = snapshot.title,
                    content = snapshot.notes,
                    image = coverUrl,
                    description = snapshot.description,
                    tracks = snapshot.tracks,
                    isPrivate = snapshot.isPrivate,
                    isCollaborative = snapshot.isCollaborative,
                )
            } else {
                MusicPlaylistEvent.build(
                    title = snapshot.title,
                    content = snapshot.notes,
                    image = coverUrl,
                    description = snapshot.description,
                    tracks = snapshot.tracks,
                    isPrivate = snapshot.isPrivate,
                    isCollaborative = snapshot.isCollaborative,
                )
            }

        account.signAndComputeBroadcast(template)
    }

    suspend fun deleteLoaded(): Boolean {
        val target = loadedEvent ?: return false
        account.delete(target, emptySet())
        return true
    }
}
