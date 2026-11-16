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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.issue.notify
import com.vitorpamplona.quartz.nip34Git.issue.repository
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * State of the "New issue" composer: the subject, the markdown body, the labels, and the media
 * attached to the body. An upload works as in the comment composer ([CommentPostViewModel]): the
 * file goes to the chosen server, its link is added to the body, and the
 * issue carries an `imeta` tag for each link still in the body when it is sent.
 */
@Stable
class GitNewIssueViewModel : ViewModel() {
    private lateinit var account: Account

    var subject by mutableStateOf("")
    var body by mutableStateOf("")
    var labels by mutableStateOf("")

    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    val mediaUploadTracker = MediaUploadTracker()
    val strippingFailureConfirmation = SuspendableConfirmation()

    private val iMetaAttachments = IMetaAttachments()

    fun init(account: Account) {
        this.account = account
    }

    fun canSend() = subject.isNotBlank() && !mediaUploadTracker.isUploading

    fun selectMedia(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    /** Uploads a hand drawing ([uploadDrawing]) and links its GIF at the end of the body. */
    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploaded = uploadDrawing(drawing, alt, account, uploader, mediaUploadTracker, uploadStill = true, onError) ?: return@launch
            iMetaAttachments.replace(uploaded.iMeta.url, uploaded.iMeta)
            appendToBody(uploaded.link)
        }
    }

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
    ) {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, uploader, useH265, stripMetadata, convertGifToMp4)
    }

    private fun uploadUnsafe(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val orchestrator = multiOrchestrator ?: return@launch

            mediaUploadTracker.startUpload(orchestrator.hasNonMedia())

            val results =
                orchestrator.upload(
                    alt,
                    contentWarningReason,
                    CompressorQuality.fromSlider(mediaQuality),
                    server,
                    account,
                    uploader,
                    useH265 = useH265,
                    stripMetadata = stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    convertGifToMp4 = convertGifToMp4,
                )

            if (results.allGood) {
                val links =
                    results.successful.mapNotNull { state ->
                        when (val uploaded = state.result) {
                            is UploadOrchestrator.OrchestratorResult.ServerResult -> {
                                val iMeta =
                                    IMetaTagBuilder(uploaded.url)
                                        .apply {
                                            hash(uploaded.fileHeader.hash)
                                            size(uploaded.fileHeader.size)
                                            uploaded.fileHeader.mimeType?.let { mimeType(it) }
                                            uploaded.fileHeader.dim?.let { dims(it) }
                                            uploaded.fileHeader.blurHash?.let { blurhash(it.blurhash) }
                                            uploaded.fileHeader.thumbHash?.let { thumbhash(it.thumbhash) }
                                            uploaded.magnet?.let { magnet(it) }
                                            uploaded.uploadedHash?.let { originalHash(it) }
                                            alt?.let { alt(it) }
                                            contentWarningReason?.let { sensitiveContent(it) }
                                        }.build()

                                iMetaAttachments.replace(iMeta.url, iMeta)
                                uploaded.url
                            }
                        }
                    }

                if (links.isNotEmpty()) appendToBody(links.joinToString(" "))
                multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()
                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            mediaUploadTracker.finishUpload()
        }
    }

    /** A plain text field has no cursor to insert at, so new links go on a line of their own at the end. */
    private fun appendToBody(links: String) {
        body =
            when {
                body.isBlank() -> links
                body.endsWith("\n") -> body + links
                else -> body + "\n" + links
            }
    }

    /**
     * Signs and broadcasts the issue for [repoNote] (run it through `launchSigner`, which outlives
     * this screen), with an `imeta` tag for each uploaded file still linked from the body.
     */
    suspend fun send(repoNote: AddressableNote) {
        val repositoryHint = repoNote.toEventHint<GitRepositoryEvent>() ?: return
        val body = body.trim()

        val usedIMetas = iMetaAttachments.filterIsIn(findURLs(body).toSet())

        // File it under every confirmed co-maintainer's coordinate too (one `a` each, and a `p` so
        // each is notified), as gitworkshop does: whichever announcement a client opens finds it.
        val coMaintainers =
            LocalCache.gitAuthority
                .coordinatesToTag(repoNote.address)
                .filter { it != repoNote.address }

        val template =
            GitIssueEvent.build(subject.trim(), body, repositoryHint, emptyList(), parseLabels(labels)) {
                coMaintainers.forEach { repository(ATag(it)) }
                notify(coMaintainers.map { PTag(it.pubKeyHex) })
                if (usedIMetas.isNotEmpty()) imetas(usedIMetas)
            }
        account.signAndComputeBroadcast(template)
    }
}

/** Splits a free-text label field on commas/whitespace, stripping any leading `#`. */
internal fun parseLabels(raw: String): List<String> =
    raw
        // Commas and lines separate labels; a space belongs to one ("good first issue").
        .split(',', '\n', '\t')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()
