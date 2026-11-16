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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)

    var selectedServer by mutableStateOf<ServerName?>(null)
    var caption by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    var onceUploaded: () -> Unit = {}

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(CompressorQuality.defaultSliderPosition)

    // Codec selection: false = H264, true = H265
    var useH265Codec by mutableStateOf(false)

    // Strip location and sensitive metadata from files before upload
    var stripMetadata by mutableStateOf(true)

    // Which NIP-71 kind videos in this batch are published as. Set by the composer's host feed.
    var videoKind: VideoPostKind = VideoPostKind.AUTO

    open fun load(
        account: Account,
        uris: ImmutableList<SelectedMedia>,
        videoKind: VideoPostKind = VideoPostKind.AUTO,
        caption: String = "",
    ) {
        this.caption = caption
        this.account = account
        this.multiOrchestrator = MultiOrchestrator(uris)
        this.selectedServer = defaultServer()
        this.stripMetadata = account.settings.stripLocationOnUpload
        this.videoKind = videoKind
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun upload(
        uploader: MediaUploader,
        accountViewModel: AccountViewModel,
        onSucess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        uploadUnsafe(uploader, accountViewModel, onSucess, onError)
    }

    fun uploadUnsafe(
        uploader: MediaUploader,
        accountViewModel: AccountViewModel,
        onSucess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myAccount = account ?: return@launch
            val serverToUse = selectedServer ?: return@launch

            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    caption,
                    if (sensitiveContent) "" else null,
                    CompressorQuality.fromSlider(mediaQualitySlider),
                    serverToUse,
                    myAccount,
                    uploader,
                    useH265Codec,
                    stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )

            if (results.allGood) {
                // It all finished successfully
                val videosAndOthers =
                    results.successful.mapNotNull {
                        val map = it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
                        if (map != null && !isImage(map.url, map.fileHeader.mimeType)) {
                            map
                        } else {
                            null
                        }
                    }

                val imageUrls =
                    results.successful
                        .mapNotNull {
                            val map = it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
                            if (map != null && isImage(map.url, map.fileHeader.mimeType)) {
                                Pair(map.url, map.fileHeader)
                            } else {
                                null
                            }
                        }.toMap()

                // Sign + publish via launchSigner so SignerExceptions surface
                // through the standard toastManager pipeline (and timed-out /
                // rejected prompts get logged instead of crashing the process).
                val videoJobs =
                    videosAndOthers.map {
                        accountViewModel.launchSigner {
                            myAccount.sendHeader(
                                url = it.url,
                                magnetUri = it.magnet,
                                headerInfo = it.fileHeader,
                                alt = caption,
                                contentWarningReason = if (sensitiveContent) "" else null,
                                originalHash = it.uploadedHash,
                                videoKind = videoKind,
                            )
                        }
                    }

                val imageJobs =
                    if (imageUrls.isNotEmpty()) {
                        listOf(
                            accountViewModel.launchSigner {
                                myAccount.sendAllAsOnePictureEvent(
                                    urlHeaderInfo = imageUrls,
                                    caption = caption,
                                    contentWarningReason = if (sensitiveContent) "" else null,
                                )
                            },
                        )
                    } else {
                        emptyList()
                    }

                videoJobs.joinAll()
                imageJobs.joinAll()

                onSucess()
                onceUploaded()
                cancelModel()
            } else {
                val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()

                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }
        }
    }

    open fun cancelModel() {
        multiOrchestrator = null
        isUploadingImage = false
        caption = ""
        selectedServer = defaultServer()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    fun canPost(): Boolean = !isUploadingImage && multiOrchestrator != null && selectedServer != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun onceUploaded(onceUploaded: () -> Unit) {
        this.onceUploaded = onceUploaded
    }
}
