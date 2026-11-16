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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.podcasts.V4VSplitEditorState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_audio_upload_failed_title
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_no_audio_url
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_publish_failed_title
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.AudioFileMetadata
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.PodcastComposerMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.podcasts.PodcastValue
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
 * Composer for a Podcasting-2.0 episode (`kind:30054`, addressable). Closely mirrors the music-track
 * composer: the user picks a cover image and an audio file, Save uploads them through Blossom/NIP-96,
 * then publishes the addressable event with the returned URLs. Power users can paste raw URLs instead.
 *
 * Edit mode (`editDTag` resolves from LocalCache) re-publishes under the same `d` tag so it replaces
 * the prior version in place; the original `pubdate` and any value-for-value block are preserved.
 */
class NewPodcastEpisodeViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val description = mutableStateOf("")
    val audioUrl = mutableStateOf("")
    val coverUrl = mutableStateOf("")
    val durationSeconds = mutableStateOf("")
    val episodeNumber = mutableStateOf("")
    val season = mutableStateOf("")
    val videoUrl = mutableStateOf("")
    val transcriptUrl = mutableStateOf("")
    val chaptersUrl = mutableStateOf("")
    val topics = mutableStateOf("")

    val isSending = mutableStateOf(false)

    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    val coverMedia = mutableStateOf<MultiOrchestrator?>(null)
    val audioMedia = mutableStateOf<MultiOrchestrator?>(null)
    val pickedAudioName = mutableStateOf<String?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()
    val selectedServer = mutableStateOf<ServerName?>(null)
    val mediaQualitySlider = mutableStateOf(CompressorQuality.defaultSliderPosition)
    val stripMetadata = mutableStateOf(true)

    private var dTag: String? = null
    private var loadedEvent: Podcasting20EpisodeEvent? = null

    /** Editable value-for-value split for this episode (overrides the show's split). */
    val splitEditor = V4VSplitEditorState()

    /** Carried across an edit so we don't drop the original publish date on save. */
    private var preservedPubDate: String? = null

    val isEditing: Boolean
        get() = loadedEvent != null

    fun init(
        accountViewModel: AccountViewModel,
        editDTag: String?,
    ) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload

        if (editDTag != null) {
            val address = Address(Podcasting20EpisodeEvent.KIND, account.userProfile().pubkeyHex, editDTag)
            (LocalCache.addressables.get(address)?.event as? Podcasting20EpisodeEvent)?.let { existing ->
                dTag = editDTag
                loadedEvent = existing
                preservedPubDate = existing.pubDate()
                splitEditor.load(existing.value())
                title.value = existing.title().orEmpty()
                description.value = existing.description().orEmpty()
                audioUrl.value =
                    existing
                        .audios()
                        .firstOrNull()
                        ?.url
                        .orEmpty()
                coverUrl.value = existing.image().orEmpty()
                durationSeconds.value = existing.durationInSeconds()?.toString().orEmpty()
                episodeNumber.value = existing.number()?.toString().orEmpty()
                season.value = existing.season()?.toString().orEmpty()
                videoUrl.value = existing.video()?.url.orEmpty()
                transcriptUrl.value = existing.transcriptUrl().orEmpty()
                chaptersUrl.value = existing.chaptersUrl().orEmpty()
                topics.value = existing.topics().joinToString(", ")
            }
        }
    }

    fun setPickedCover(uris: ImmutableList<SelectedMedia>) {
        coverMedia.value = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun clearPickedCover() {
        coverMedia.value = null
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
        pickedAudioName.value = uri.uri.lastPathSegmentOrNull()?.substringAfterLast('/')

        viewModelScope.launch {
            val probed = probe(uri.uri) ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                probed.durationSeconds?.let { durationSeconds.value = it.toString() }
                if (title.value.isBlank()) probed.title?.let { title.value = it }
            }
        }
    }

    fun clearPickedAudio() {
        audioMedia.value = null
        pickedAudioName.value = null
    }

    /** Valid with a title and a resolvable audio source (picked file or a pasted URL). */
    fun isValid(): Boolean = title.value.isNotBlank() && (audioMedia.value != null || audioUrl.value.isNotBlank())

    fun saveAndPublish(accountViewModel: AccountViewModel) {
        if (isSending.value) return

        val server = selectedServer.value
        if (server == null) {
            accountViewModel.toastManager.toast(
                Res.string.upload_no_server_title,
                Res.string.upload_no_server_message,
            )
            return
        }

        val snapshot =
            Snapshot(
                title = title.value.trim(),
                description = description.value.trim().ifBlank { null },
                durationSeconds = durationSeconds.value.trim().toLongOrNull(),
                episodeNumber = episodeNumber.value.trim().toIntOrNull(),
                season = season.value.trim().toIntOrNull(),
                videoUrl = videoUrl.value.trim().ifBlank { null },
                transcriptUrl = transcriptUrl.value.trim().ifBlank { null },
                chaptersUrl = chaptersUrl.value.trim().ifBlank { null },
                topics = PodcastComposerMedia.parseCsv(topics.value),
                value = splitEditor.toPodcastValue(),
                coverOrchestrator = coverMedia.value,
                audioOrchestrator = audioMedia.value,
                existingCoverUrl = coverUrl.value.trim().ifBlank { null },
                existingAudioUrl = audioUrl.value.trim(),
                server = server,
                quality = CompressorQuality.fromSlider(mediaQualitySlider.value),
                stripMetadata = stripMetadata.value,
                uploader = accountViewModel.mediaUploader,
            )

        isSending.value = true

        accountViewModel.launchSigner {
            try {
                val (newCoverUrl, newAudioUrl) = performParallelUploads(snapshot)
                val finalCoverUrl = newCoverUrl ?: snapshot.existingCoverUrl
                val finalAudioUrl = newAudioUrl ?: snapshot.existingAudioUrl
                if (finalAudioUrl.isBlank()) {
                    accountViewModel.toastManager.toast(
                        Res.string.podcast_audio_upload_failed_title,
                        Res.string.podcast_episode_no_audio_url,
                    )
                    return@launchSigner
                }

                publishEpisode(snapshot, finalCoverUrl, finalAudioUrl)

                account.settings.changeDefaultFileServer(snapshot.server)
                account.settings.changeStripLocationOnUpload(snapshot.stripMetadata)

                withContext(Dispatchers.Main.immediate) {
                    coverMedia.value = null
                    audioMedia.value = null
                    pickedAudioName.value = null
                    if (newCoverUrl != null) coverUrl.value = newCoverUrl
                    if (newAudioUrl != null) audioUrl.value = newAudioUrl
                }
                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast(
                    Res.string.podcast_episode_publish_failed_title,
                    t.message ?: t::class.simpleName.orEmpty(),
                    t,
                )
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }

    private class Snapshot(
        val title: String,
        val description: String?,
        val durationSeconds: Long?,
        val episodeNumber: Int?,
        val season: Int?,
        val videoUrl: String?,
        val transcriptUrl: String?,
        val chaptersUrl: String?,
        val topics: List<String>,
        val value: PodcastValue?,
        val coverOrchestrator: MultiOrchestrator?,
        val audioOrchestrator: MultiOrchestrator?,
        val existingCoverUrl: String?,
        val existingAudioUrl: String,
        val server: ServerName,
        val quality: CompressorQuality,
        val stripMetadata: Boolean,
        val uploader: MediaUploader,
    )

    private suspend fun performParallelUploads(snapshot: Snapshot): Pair<String?, String?> =
        coroutineScope {
            val deferreds =
                listOf(
                    async { snapshot.coverOrchestrator?.let { uploadOne(it, "cover", snapshot) } },
                    async { snapshot.audioOrchestrator?.let { uploadOne(it, "audio", snapshot) } },
                )
            val results = deferreds.awaitAll()
            results[0] to results[1]
        }

    private suspend fun uploadOne(
        orchestrator: MultiOrchestrator,
        kind: String,
        snapshot: Snapshot,
    ): String =
        PodcastComposerMedia.upload(
            orchestrator = orchestrator,
            kind = kind,
            account = account,
            server = snapshot.server,
            quality = snapshot.quality,
            stripMetadata = snapshot.stripMetadata,
            alt = snapshot.title.ifBlank { null },
            uploader = snapshot.uploader,
            onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
        )

    private suspend fun publishEpisode(
        snapshot: Snapshot,
        coverUrl: String?,
        audioUrl: String,
    ) {
        // Audio/video MIME isn't tracked once uploaded (the URL is enough; the player sniffs the
        // type). The summary lives in the `description` tag; `content` stays empty so the episode
        // renderer doesn't show the same text twice (it renders the tag and the markdown body apart).
        val template =
            Podcasting20EpisodeEvent.build(
                dTag = dTag ?: PodcastComposerMedia.generateDTag("episode"),
                title = snapshot.title,
                audios = listOf(PodcastAudio(audioUrl, null)),
                pubdate = preservedPubDate ?: PodcastComposerMedia.rfc2822Now(),
                description = snapshot.description,
                image = coverUrl?.ifBlank { null },
                durationInSeconds = snapshot.durationSeconds,
                video = snapshot.videoUrl?.let { PodcastAudio(it, null) },
                episodeNumber = snapshot.episodeNumber,
                season = snapshot.season,
                transcriptUrl = snapshot.transcriptUrl,
                chaptersUrl = snapshot.chaptersUrl,
                value = snapshot.value,
                topics = snapshot.topics,
            )
        account.signAndComputeBroadcast(template)
    }

    suspend fun deleteLoaded(): Boolean {
        val target = loadedEvent ?: return false
        val note = LocalCache.getOrCreateAddressableNote(target.address())
        account.delete(note)
        return true
    }
}
