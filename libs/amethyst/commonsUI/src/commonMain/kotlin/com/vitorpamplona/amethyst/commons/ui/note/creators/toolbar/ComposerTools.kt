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
package com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromFiles
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDateButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList

/** What a composer's media tools ([ComposerMediaTools]) need from its view model. */
interface IComposerMediaTools {
    val accountViewModel: AccountViewModel
    val isUploadingImage: Boolean
    val isUploadingFile: Boolean

    fun selectImage(uris: ImmutableList<SelectedMedia>)

    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    )
}

/** What a composer's post options ([ComposerPostOptionTools]) need from its view model. */
interface IComposerPostOptions {
    var wantsForwardZapTo: Boolean
    val canAddZapRaiser: Boolean
    var wantsZapRaiser: Boolean
    var wantsToMarkAsSensitive: Boolean
    var wantsExpirationDate: Boolean
    var wantsToAddGeoHash: Boolean
    var wantsSecretEmoji: Boolean
    val canAddInvoice: Boolean
    var wantsInvoice: Boolean

    fun toggleMarkAsSensitive()

    fun toggleExpirationDate()

    fun hasLnAddress(): Boolean
}

/**
 * The media tools every composer offers, in one order: gallery, files, camera, video (where the
 * platform has a camera) and drawing. Put them in a [ComposerToolbar].
 */
@Composable
fun ComposerMediaTools(vm: IComposerMediaTools) {
    SelectFromGallery(
        isUploading = vm.isUploadingImage,
        enabled = !vm.isUploadingFile,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier,
    ) {
        vm.selectImage(it)
    }

    SelectFromFiles(
        isUploading = vm.isUploadingFile,
        enabled = !vm.isUploadingImage,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier,
    ) {
        vm.selectImage(it)
    }

    TakePictureButton(onPictureTaken = { vm.selectImage(it) })

    TakeVideoButton(onVideoTaken = { vm.selectImage(it) })

    DrawingButton(vm.accountViewModel, enabled = !vm.isUploadingImage && !vm.isUploadingFile) { drawing, alt ->
        vm.uploadDrawing(drawing, alt, vm.accountViewModel.toastManager::toast)
    }
}

/**
 * The options every public composer offers, in one order: zap split, zapraiser (where allowed),
 * content warning, expiration, location, secret emoji and invoice (with a lightning address). Put
 * them in a [ComposerToolbar].
 */
@Composable
fun ComposerPostOptionTools(vm: IComposerPostOptions) {
    ForwardZapToButton(vm.wantsForwardZapTo) {
        vm.wantsForwardZapTo = !vm.wantsForwardZapTo
    }

    if (vm.canAddZapRaiser) {
        AddZapraiserButton(vm.wantsZapRaiser) {
            vm.wantsZapRaiser = !vm.wantsZapRaiser
        }
    }

    MarkAsSensitiveButton(vm.wantsToMarkAsSensitive) {
        vm.toggleMarkAsSensitive()
    }

    ExpirationDateButton(vm.wantsExpirationDate) {
        vm.toggleExpirationDate()
    }

    AddGeoHashButton(vm.wantsToAddGeoHash) {
        vm.wantsToAddGeoHash = !vm.wantsToAddGeoHash
    }

    AddSecretEmojiButton(vm.wantsSecretEmoji) {
        vm.wantsSecretEmoji = !vm.wantsSecretEmoji
    }

    if (vm.canAddInvoice && vm.hasLnAddress()) {
        AddLnInvoiceButton(vm.wantsInvoice) {
            vm.wantsInvoice = !vm.wantsInvoice
        }
    }
}
