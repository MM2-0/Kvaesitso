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
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.music.musicFormatTagFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.music_track_no_audio_url
import com.vitorpamplona.amethyst.commons.resources.music_track_send_failed_title
import com.vitorpamplona.amethyst.commons.resources.podcast_audio_upload_failed_title
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.AudioFileMetadata
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer for a kind-36787 Music Track. Mirrors the Badge composer flow: the user picks the
 * cover image and the audio file up front (Save uploads them through Blossom/NIP-96, then
 * publishes the addressable event with the returned URLs). Power users can still paste raw
 * URLs into the text fields below the pickers when they're sourcing audio from elsewhere.
 *
 * In edit mode (`editDTag` set AND the event resolves from LocalCache), publishes via
 * [MusicTrackEvent.edit] so every tag the composer doesn't surface (video, released,
 * track_number, format, bitrate, sample_rate, language, explicit, extra `t` hashtags,
 * zap-split tags, etc) is preserved across save. When `editDTag` is set but the event isn't in
 * cache, falls back to create-mode so the user isn't trapped on a Delete button that wouldn't
 * do anything.
 */
class NewMusicTrackViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val artist = mutableStateOf("")
    val audioUrl = mutableStateOf("")
    val coverUrl = mutableStateOf("")
    val album = mutableStateOf("")
    val durationSeconds = mutableStateOf("")
    val description = mutableStateOf("")

    /**
     * Single in-flight flag covering both the parallel uploads and the subsequent publish.
     * Drives the Send button's spinner, gates double-tap, and overlays each picker with a
     * progress indicator. Kept as one boolean rather than separate upload/publish flags
     * because the user experience is "one Send action" — the two phases blur into each
     * other and showing them as separate states only confuses the picture.
     */
    val isSending = mutableStateOf(false)

    /**
     * One-shot success channel. Emits exactly once when an upload+publish round succeeds;
     * the screen collects it and pops back. We can't rely on a callback because the
     * coroutine runs on [AccountViewModel.viewModelScope] (so the work survives screen
     * dismissal), but the screen's nav handle becomes stale the moment the user leaves.
     * Tying the popBack to a flow the screen subscribes to keeps it correct: if the user
     * has already left, no one is listening and nothing pops; if they're still on screen,
     * the dismissal fires.
     */
    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    /**
     * Two separate orchestrators because the cover image and the audio file go through
     * different compression paths (the audio path skips image compression and the cover path
     * goes through it). Sharing one orchestrator would force one path on both files.
     */
    val coverMedia = mutableStateOf<MultiOrchestrator?>(null)
    val audioMedia = mutableStateOf<MultiOrchestrator?>(null)
    val pickedAudioName = mutableStateOf<String?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()

    val selectedServer = mutableStateOf<ServerName?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED — matches Badge's slider semantics.
    val mediaQualitySlider = mutableStateOf(CompressorQuality.defaultSliderPosition)
    val stripMetadata = mutableStateOf(true)

    /** Stable d-tag for the addressable: null = create-new, non-null = edit-existing. */
    private var dTag: String? = null

    /** The loaded event in edit mode; needed for `edit()` + NIP-09 deletion. */
    private var loadedEvent: MusicTrackEvent? = null

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
            val existingAddress = Address(MusicTrackEvent.KIND, account.userProfile().pubkeyHex, editDTag)
            val existingNote = LocalCache.addressables.get(existingAddress)
            (existingNote?.event as? MusicTrackEvent)?.let { existing ->
                dTag = editDTag
                loadedEvent = existing
                title.value = existing.title().orEmpty()
                artist.value = existing.artist().orEmpty()
                audioUrl.value = existing.url().orEmpty()
                coverUrl.value = existing.image().orEmpty()
                album.value = existing.album().orEmpty()
                durationSeconds.value = existing.duration()?.toString().orEmpty()
                description.value = existing.content
            }
            // If the lookup fails, dTag stays null and the screen renders as create-mode.
        }
    }

    fun setPickedCover(uris: ImmutableList<SelectedMedia>) {
        coverMedia.value = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun clearPickedCover() {
        coverMedia.value = null
        // Also drop the already-published cover URL so the remove gesture removes the existing
        // artwork on save (MusicTrackEvent.edit treats a blank image as "remove the tag").
        coverUrl.value = ""
    }

    fun setPickedAudio(
        uri: SelectedMedia?,
        probe: suspend (MediaUri) -> AudioFileMetadata?,
    ) {
        if (uri == null) {
            audioMedia.value = null
            pickedAudioName.value = null
            return
        }

        audioMedia.value = MultiOrchestrator(persistentListOf(uri))
        // Fall back to the last path segment of the content:// URI — Android picker URIs
        // rarely expose a clean filename, but the segment is at least stable across recomposes.
        pickedAudioName.value = uri.uri.lastPathSegmentOrNull()?.substringAfterLast('/')

        // Auto-fill title/artist/album/duration from the picked file's embedded metadata so
        // the user doesn't have to retype what's already in the ID3 / mp4 / FLAC tags.
        viewModelScope.launch {
            val probed = probe(uri.uri) ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                // Only fill empty fields — don't clobber whatever the user already typed.
                // Duration is the exception: it's a derived number, not an opinion the user
                // typed, so overwrite if the file actually reports one.
                probed.durationSeconds?.let { durationSeconds.value = it.toString() }
                if (title.value.isBlank()) probed.title?.let { title.value = it }
                if (artist.value.isBlank()) probed.artist?.let { artist.value = it }
                if (album.value.isBlank()) probed.album?.let { album.value = it }
            }
        }
    }

    fun clearPickedAudio() {
        audioMedia.value = null
        pickedAudioName.value = null
    }

    /**
     * Valid when we have a title, an artist, and a way to resolve an audio URL — either a
     * picked file ready to upload, or a non-blank URL in the text field.
     */
    fun isValid(): Boolean =
        title.value.isNotBlank() &&
            artist.value.isNotBlank() &&
            (audioMedia.value != null || audioUrl.value.isNotBlank())

    /**
     * Upload any picked files (cover + audio) in parallel, then sign and broadcast the
     * event with the resulting URLs. The whole operation runs on
     * [AccountViewModel.viewModelScope] via `launchSigner`, so it keeps running even if
     * the user leaves the screen — they get a toast notification when it finishes either
     * way, and we don't lose half-uploaded data.
     *
     * Errors are reported through the global toast manager rather than a callback because
     * the screen may already be gone by the time we know the outcome.
     */
    fun saveAndPublish(accountViewModel: AccountViewModel) {
        if (isSending.value) return // double-tap guard

        // Snapshot every input the upload needs into immutable locals BEFORE launching, so
        // user edits to the form after pressing Send don't poison the in-flight publish
        // (and so the screen-bound mutableState references survive even after the VM is
        // cleared by the user navigating away).
        val acc = account
        val server = selectedServer.value
        if (server == null) {
            accountViewModel.toastManager.toast(
                Res.string.upload_no_server_title,
                Res.string.upload_no_server_message,
            )
            return
        }
        val snapshot =
            SendSnapshot(
                title = title.value.trim(),
                artist = artist.value.trim(),
                description = description.value,
                album = album.value.trim().ifBlank { null },
                duration = durationSeconds.value.trim().toIntOrNull(),
                coverOrchestrator = coverMedia.value,
                audioOrchestrator = audioMedia.value,
                existingCoverUrl = coverUrl.value.trim().ifBlank { null },
                existingAudioUrl = audioUrl.value.trim(),
                server = server,
                quality = CompressorQuality.fromSlider(mediaQualitySlider.value),
                stripMetadata = stripMetadata.value,
                loadedEvent = loadedEvent,
                uploader = accountViewModel.mediaUploader,
            )

        isSending.value = true

        // launchSigner runs on AccountViewModel.viewModelScope, which outlives this
        // per-screen VM. So if the user navigates away mid-upload, the work keeps going
        // and the toast still fires on completion.
        accountViewModel.launchSigner {
            try {
                val (newCover, newAudio) = performParallelUploads(snapshot, accountViewModel)
                val newCoverUrl = newCover?.url
                val newAudioUrl = newAudio?.url

                val finalCoverUrl = newCoverUrl ?: snapshot.existingCoverUrl
                val finalAudioUrl = newAudioUrl ?: snapshot.existingAudioUrl
                if (finalAudioUrl.isBlank()) {
                    accountViewModel.toastManager.toast(
                        Res.string.podcast_audio_upload_failed_title,
                        Res.string.music_track_no_audio_url,
                    )
                    return@launchSigner
                }

                // A fresh upload says what it is, so readers don't have to open an `.mp4` to find out.
                publishEvent(snapshot, finalCoverUrl, finalAudioUrl, newAudio?.mimeType?.let(::musicFormatTagFor))

                // Remember the server + strip-metadata choice so the user doesn't have to
                // re-pick them on the next track.
                acc.settings.changeDefaultFileServer(snapshot.server)
                acc.settings.changeStripLocationOnUpload(snapshot.stripMetadata)

                withContext(Dispatchers.Main.immediate) {
                    // Only NOW clear the pickers — keeping them populated through the
                    // whole upload+publish phase means each picker tile keeps showing the
                    // user the file it's working on, so they don't think the cover is
                    // already done while the audio is still uploading.
                    coverMedia.value = null
                    audioMedia.value = null
                    pickedAudioName.value = null
                    if (newCoverUrl != null) coverUrl.value = newCoverUrl
                    if (newAudioUrl != null) audioUrl.value = newAudioUrl
                }
                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast(
                    Res.string.music_track_send_failed_title,
                    t.message ?: t::class.simpleName.orEmpty(),
                    t,
                )
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }

    /**
     * Snapshot of every input the send coroutine needs. Captured before launching so the
     * coroutine sees a stable view even after the VM is cleared, and so user edits to the
     * form after pressing Send don't change what gets uploaded.
     */
    private data class SendSnapshot(
        val title: String,
        val artist: String,
        val description: String,
        val album: String?,
        val duration: Int?,
        val coverOrchestrator: MultiOrchestrator?,
        val audioOrchestrator: MultiOrchestrator?,
        val existingCoverUrl: String?,
        val existingAudioUrl: String,
        val server: ServerName,
        val quality: CompressorQuality,
        val stripMetadata: Boolean,
        val loadedEvent: MusicTrackEvent?,
        val uploader: MediaUploader,
    )

    /**
     * Uploads the cover and audio in parallel via `async`/`awaitAll`. Each side is
     * independent: if cover fails, audio still completes (and vice versa); the first
     * failure throws and propagates out through the catch in [saveAndPublish].
     *
     * Returns `(cover?, audio?)`. A null means "no orchestrator was provided for
     * that slot" (the caller falls back to the existing URL from the snapshot).
     */
    private suspend fun performParallelUploads(
        snapshot: SendSnapshot,
        accountViewModel: AccountViewModel,
    ): Pair<UploadedFile?, UploadedFile?> =
        coroutineScope {
            val deferreds =
                listOf(
                    async {
                        snapshot.coverOrchestrator?.let { runSingleUpload(it, snapshot, "cover") }
                    },
                    async {
                        snapshot.audioOrchestrator?.let { runSingleUpload(it, snapshot, "audio") }
                    },
                )
            val results = deferreds.awaitAll()
            results[0] to results[1]
        }

    private suspend fun runSingleUpload(
        orch: MultiOrchestrator,
        snapshot: SendSnapshot,
        kind: String,
    ): UploadedFile {
        val res =
            orch.upload(
                alt = snapshot.title.ifBlank { null },
                contentWarningReason = null,
                mediaQuality = snapshot.quality,
                server = snapshot.server,
                account = account,
                uploader = snapshot.uploader,
                useH265 = false,
                stripMetadata = snapshot.stripMetadata,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            )
        if (!res.allGood) {
            throw UploadException(kind, formatUploadErrors(res.errors))
        }
        return firstUploadedFile(res.successful)
            ?: throw UploadException(kind, "Server didn't return a URL for the uploaded $kind.")
    }

    /** Where an upload landed, and what it was before upload (Blossom may relabel an M4A `video/mp4`). */
    private class UploadedFile(
        val url: String,
        val mimeType: String?,
    )

    private class UploadException(
        kind: String,
        details: String,
    ) : Exception("$kind upload failed: $details")

    private fun firstUploadedFile(successful: List<UploadingState.Finished>): UploadedFile? =
        successful
            .firstNotNullOfOrNull { it.result as? UploadOrchestrator.OrchestratorResult.ServerResult }
            ?.let { UploadedFile(it.url, it.mimeTypeBeforeEncryption ?: it.fileHeader.mimeType) }

    private suspend fun formatUploadErrors(errors: List<UploadingState.Error>): String =
        errors
            .map { loadStringRes(it.errorResource, *it.params) }
            .distinct()
            .joinToString(".\n")

    private suspend fun publishEvent(
        snapshot: SendSnapshot,
        coverUrl: String?,
        audioUrl: String,
        format: String?,
    ) {
        val existing = snapshot.loadedEvent
        val template =
            if (existing != null) {
                MusicTrackEvent.edit(
                    earlierVersion = existing,
                    title = snapshot.title,
                    artist = snapshot.artist,
                    url = audioUrl,
                    description = snapshot.description,
                    image = coverUrl,
                    album = snapshot.album,
                    duration = snapshot.duration,
                    format = format,
                )
            } else {
                MusicTrackEvent.build(
                    title = snapshot.title,
                    artist = snapshot.artist,
                    url = audioUrl,
                    description = snapshot.description,
                    image = coverUrl,
                    album = snapshot.album,
                    duration = snapshot.duration,
                    format = format,
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
