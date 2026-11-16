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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.podcasts.V4VSplitEditorState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_show_save_failed_title
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Editor for a creator's Podcasting-2.0 show metadata — the single replaceable `kind:30078`
 * (`d="podcast-metadata"`) event whose JSON body holds the channel-level fields. There is one per
 * account, so this is always an edit-or-create of the same address; saving replaces it in place.
 *
 * Mirrors the profile-metadata editor: text fields + a cover upload, no audio. Any value-for-value
 * block already on the event is preserved across save (the splits editor is separate).
 */
class EditPodcastShowViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val description = mutableStateOf("")
    val author = mutableStateOf("")
    val email = mutableStateOf("")
    val coverUrl = mutableStateOf("")
    val website = mutableStateOf("")
    val language = mutableStateOf("")
    val categories = mutableStateOf("")
    val funding = mutableStateOf("")
    val copyright = mutableStateOf("")

    /** "episodic" or "serial" (Podcasting 2.0), or blank for unset. */
    val type = mutableStateOf("")
    val explicit = mutableStateOf(false)
    val complete = mutableStateOf(false)
    val locked = mutableStateOf(false)

    val isSending = mutableStateOf(false)

    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    val coverMedia = mutableStateOf<MultiOrchestrator?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()
    val selectedServer = mutableStateOf<ServerName?>(null)
    val mediaQualitySlider = mutableStateOf(CompressorQuality.defaultSliderPosition)
    val stripMetadata = mutableStateOf(true)

    /** Editable value-for-value split for the show. */
    val splitEditor = V4VSplitEditorState()

    /** Fields the editor doesn't surface but must not drop on save. */
    private var preservedGuid: String? = null
    private var hasExisting = false

    fun init(accountViewModel: AccountViewModel) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload

        val address = Address(AppSpecificDataEvent.KIND, account.userProfile().pubkeyHex, Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)
        val existing = (LocalCache.addressables.get(address)?.event as? AppSpecificDataEvent)?.let { Podcasting20PodcastMetadata.parse(it) }
        if (existing != null) {
            hasExisting = true
            preservedGuid = existing.guid()
            splitEditor.load(existing.showValue())
            title.value = existing.showTitle().orEmpty()
            description.value = existing.showDescription().orEmpty()
            author.value = existing.showAuthor().orEmpty()
            email.value = existing.email().orEmpty()
            coverUrl.value = existing.showImage().orEmpty()
            website.value = existing.showWebsites().firstOrNull().orEmpty()
            language.value = existing.language().orEmpty()
            categories.value = existing.showCategories().joinToString(", ")
            funding.value = existing.showFundingUrls().joinToString(", ")
            copyright.value = existing.showCopyright().orEmpty()
            type.value = existing.type().orEmpty()
            explicit.value = existing.showIsExplicit()
            complete.value = existing.showIsComplete()
            locked.value = existing.isLocked()
        }
    }

    fun setPickedCover(uris: ImmutableList<SelectedMedia>) {
        coverMedia.value = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun clearPickedCover() {
        coverMedia.value = null
        coverUrl.value = ""
    }

    fun isValid(): Boolean = title.value.isNotBlank()

    fun saveAndPublish(accountViewModel: AccountViewModel) {
        if (isSending.value) return

        val coverOrch = coverMedia.value
        val server = selectedServer.value
        if (coverOrch != null && server == null) {
            accountViewModel.toastManager.toast(
                Res.string.upload_no_server_title,
                Res.string.upload_no_server_message,
            )
            return
        }

        val snapshot =
            Snapshot(
                content =
                    Podcasting20PodcastMetadata.Content(
                        title = title.value.trim(),
                        description = description.value.trim().ifBlank { null },
                        author = author.value.trim().ifBlank { null },
                        email = email.value.trim().ifBlank { null },
                        image = coverUrl.value.trim().ifBlank { null },
                        language = language.value.trim().ifBlank { null },
                        categories = PodcastComposerMedia.parseCsv(categories.value),
                        explicit = explicit.value.takeIf { it },
                        website = website.value.trim().ifBlank { null },
                        copyright = copyright.value.trim().ifBlank { null },
                        funding = PodcastComposerMedia.parseCsv(funding.value),
                        locked = locked.value.takeIf { it },
                        type = type.value.trim().ifBlank { null },
                        complete = complete.value.takeIf { it },
                        guid = preservedGuid,
                        value = splitEditor.toPodcastValue(),
                    ),
                coverOrchestrator = coverOrch,
                server = server,
                quality = CompressorQuality.fromSlider(mediaQualitySlider.value),
                stripMetadata = stripMetadata.value,
                uploader = accountViewModel.mediaUploader,
            )

        isSending.value = true
        accountViewModel.launchSigner {
            try {
                val newCoverUrl =
                    snapshot.coverOrchestrator?.let {
                        PodcastComposerMedia.upload(
                            orchestrator = it,
                            kind = "cover",
                            account = account,
                            server = snapshot.server!!,
                            quality = snapshot.quality,
                            stripMetadata = snapshot.stripMetadata,
                            alt = snapshot.content.title,
                            uploader = snapshot.uploader,
                            onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                        )
                    }

                val finalContent = newCoverUrl?.let { snapshot.content.copyWithImage(it) } ?: snapshot.content
                account.signAndComputeBroadcast(Podcasting20PodcastMetadata.build(finalContent))

                if (snapshot.coverOrchestrator != null) {
                    account.settings.changeDefaultFileServer(snapshot.server!!)
                    account.settings.changeStripLocationOnUpload(snapshot.stripMetadata)
                }

                withContext(Dispatchers.Main.immediate) {
                    coverMedia.value = null
                    if (newCoverUrl != null) coverUrl.value = newCoverUrl
                }
                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast(
                    Res.string.podcast_show_save_failed_title,
                    t.message ?: t::class.simpleName ?: "",
                    t,
                )
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }

    private class Snapshot(
        val content: Podcasting20PodcastMetadata.Content,
        val coverOrchestrator: MultiOrchestrator?,
        val server: ServerName?,
        val quality: CompressorQuality,
        val stripMetadata: Boolean,
        val uploader: MediaUploader,
    )
}

/** Copies a metadata content with a new image URL (Content has no copy() — it's a plain class). */
private fun Podcasting20PodcastMetadata.Content.copyWithImage(image: String): Podcasting20PodcastMetadata.Content =
    Podcasting20PodcastMetadata.Content(
        title = title,
        description = description,
        author = author,
        email = email,
        image = image,
        language = language,
        categories = categories,
        explicit = explicit,
        website = website,
        copyright = copyright,
        funding = funding,
        locked = locked,
        type = type,
        complete = complete,
        guid = guid,
        value = value,
    )
