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
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_no_url
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_publish_failed_title
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_upload_failed_title
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_message
import com.vitorpamplona.amethyst.commons.resources.upload_no_server_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Composer for a Podcasting-2.0 trailer (`kind:30055`, addressable). A short preview clip: a title,
 * one audio/video file (uploaded or pasted as a URL), and an optional season number. Always
 * create-new — each trailer gets a fresh `d` tag.
 */
class NewPodcastTrailerViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val url = mutableStateOf("")
    val season = mutableStateOf("")
    val pickedMimeType = mutableStateOf<String?>(null)

    val isSending = mutableStateOf(false)

    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    val media = mutableStateOf<MultiOrchestrator?>(null)
    val pickedName = mutableStateOf<String?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()
    val selectedServer = mutableStateOf<ServerName?>(null)
    val mediaQualitySlider = mutableStateOf(CompressorQuality.defaultSliderPosition)
    val stripMetadata = mutableStateOf(true)

    fun init(accountViewModel: AccountViewModel) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload
    }

    fun setPickedMedia(uri: SelectedMedia?) {
        if (uri == null) {
            media.value = null
            pickedName.value = null
            pickedMimeType.value = null
            return
        }
        media.value = MultiOrchestrator(persistentListOf(uri))
        pickedName.value = uri.uri.lastPathSegmentOrNull()?.substringAfterLast('/')
        pickedMimeType.value = uri.mimeType
    }

    fun clearPickedMedia() {
        media.value = null
        pickedName.value = null
        pickedMimeType.value = null
    }

    fun isValid(): Boolean = title.value.isNotBlank() && (media.value != null || url.value.isNotBlank())

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

        val titleSnap = title.value.trim()
        val urlSnap = url.value.trim()
        val seasonSnap = season.value.trim().toIntOrNull()
        val mimeSnap = pickedMimeType.value
        val mediaSnap = media.value
        val quality = CompressorQuality.fromSlider(mediaQualitySlider.value)
        val strip = stripMetadata.value

        isSending.value = true
        accountViewModel.launchSigner {
            try {
                val finalUrl =
                    if (mediaSnap != null) {
                        PodcastComposerMedia.upload(
                            orchestrator = mediaSnap,
                            kind = "trailer",
                            account = account,
                            server = server,
                            quality = quality,
                            stripMetadata = strip,
                            alt = titleSnap.ifBlank { null },
                            uploader = accountViewModel.mediaUploader,
                            onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                        )
                    } else {
                        urlSnap
                    }
                if (finalUrl.isBlank()) {
                    accountViewModel.toastManager.toast(Res.string.podcast_trailer_upload_failed_title, Res.string.podcast_trailer_no_url)
                    return@launchSigner
                }

                val template =
                    Podcasting20TrailerEvent.build(
                        dTag = PodcastComposerMedia.generateDTag("trailer"),
                        title = titleSnap,
                        url = finalUrl,
                        pubdate = PodcastComposerMedia.rfc2822Now(),
                        mimeType = mimeSnap,
                        season = seasonSnap,
                    )
                account.signAndComputeBroadcast(template)

                account.settings.changeDefaultFileServer(server)
                account.settings.changeStripLocationOnUpload(strip)

                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast(Res.string.podcast_trailer_publish_failed_title, t.message ?: t::class.simpleName ?: "", t)
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }
}
