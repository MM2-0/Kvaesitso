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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.badges.post

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.upload_no_image_url_returned
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip58Badges.definition.tags.ThumbTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Drives the "new badge" creation flow: uploads the user-picked image (at the
 * chosen server / compression), then publishes a NIP-58 BadgeDefinitionEvent
 * (kind 30009) with an auto-generated UUID d-tag, the uploaded URL as both
 * `image` and `thumb`, and the user-provided name / description.
 *
 * Intentionally does NOT publish the event unless the image upload succeeds —
 * otherwise we'd announce a badge that references a URL that doesn't exist.
 */
@Stable
class NewBadgeModel : ViewModel() {
    var account: Account? = null

    var isUploading by mutableStateOf(false)

    var selectedServer by mutableStateOf<ServerName?>(null)
    var name by mutableStateOf("")
    var description by mutableStateOf("")

    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(CompressorQuality.defaultSliderPosition)

    var stripMetadata by mutableStateOf(true)

    var onceUploaded: () -> Unit = {}

    fun init(account: Account) {
        if (this.account == account) return
        this.account = account
        this.selectedServer = defaultServer()
        this.stripMetadata = account.settings.stripLocationOnUpload
    }

    fun load(
        account: Account,
        uris: ImmutableList<SelectedMedia>,
    ) {
        this.account = account
        this.multiOrchestrator = MultiOrchestrator(uris)
        this.selectedServer = defaultServer()
        this.stripMetadata = account.settings.stripLocationOnUpload
        this.name = ""
        this.description = ""
    }

    fun setPickedMedia(uris: ImmutableList<SelectedMedia>) {
        this.multiOrchestrator = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun hasPickedImage(): Boolean = multiOrchestrator != null

    fun canPost(): Boolean =
        !isUploading &&
            multiOrchestrator != null &&
            selectedServer != null &&
            name.isNotBlank()

    fun upload(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        uploadUnsafe(uploader, onSuccess, onError)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun uploadUnsafe(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myAccount = account ?: return@launch
            val serverToUse = selectedServer ?: return@launch
            val orch = multiOrchestrator ?: return@launch

            isUploading = true

            val results =
                orch.upload(
                    alt = name,
                    contentWarningReason = null,
                    mediaQuality = CompressorQuality.fromSlider(mediaQualitySlider),
                    server = serverToUse,
                    account = myAccount,
                    uploader = uploader,
                    useH265 = false,
                    stripMetadata = stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )

            if (!results.allGood) {
                val messages =
                    results.errors
                        .map { loadStringRes(it.errorResource, *it.params) }
                        .distinct()
                        .joinToString(".\n")
                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), messages)
                isUploading = false
                return@launch
            }

            val uploaded =
                results.successful.firstNotNullOfOrNull {
                    it.result as? UploadOrchestrator.OrchestratorResult.ServerResult
                }

            if (uploaded == null) {
                onError(
                    loadStringRes(Res.string.failed_to_upload_media_no_details),
                    loadStringRes(Res.string.upload_no_image_url_returned),
                )
                isUploading = false
                return@launch
            }

            val imageUrl = uploaded.url
            val dimensions = uploaded.fileHeader.dim

            try {
                myAccount.sendBadgeDefinition(
                    badgeId = Uuid.random().toString(),
                    name = name.trim(),
                    imageUrl = imageUrl,
                    imageDim = dimensions,
                    description = description.trim().ifBlank { null },
                    // NIP-58 thumb is optional; we emit it pointing at the same
                    // uploaded asset so clients that only honor the thumb tag also
                    // see the badge image.
                    thumbs = listOf(ThumbTag(imageUrl, dimensions)),
                )
            } catch (_: SignerExceptions.ReadOnlyException) {
                onError(
                    loadStringRes(Res.string.read_only_user),
                    loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
                )
                isUploading = false
                return@launch
            } catch (_: SignerExceptions) {
                // The user rejected it in their signer, or the signer was unavailable: nothing was
                // published, so keep the form open instead of reporting success.
                isUploading = false
                return@launch
            }

            myAccount.settings.changeDefaultFileServer(serverToUse)
            myAccount.settings.changeStripLocationOnUpload(stripMetadata)

            onSuccess()
            onceUploaded()
            cancelModel()
        }
    }

    fun cancelModel() {
        multiOrchestrator = null
        isUploading = false
        name = ""
        description = ""
        selectedServer = defaultServer()
    }

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun onceUploaded(action: () -> Unit) {
        this.onceUploaded = action
    }
}
