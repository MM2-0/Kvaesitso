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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.list.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable
class EmojiPackMetadataViewModel : ViewModel() {
    private lateinit var account: Account

    var pack by mutableStateOf<OwnedEmojiPack?>(null)
    val isNewPack by derivedStateOf { pack == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    var pickedMedia by mutableStateOf<SelectedMedia?>(null)
        private set

    var isWorking by mutableStateOf(false)

    val canPost by derivedStateOf {
        !isWorking && name.value.text.isNotBlank()
    }

    fun hasImage(): Boolean = pickedMedia != null || picture.value.text.isNotBlank()

    fun init(accountViewModel: AccountViewModel) {
        this.account = accountViewModel.account
    }

    fun new() {
        pack = null
        clear()
    }

    fun load(dTag: String) {
        val existing = account.ownedEmojiPacks.getPack(dTag)
        pack = existing
        name.value = TextFieldValue(existing?.title ?: "")
        picture.value = TextFieldValue(existing?.image ?: "")
        description.value = TextFieldValue(existing?.description ?: "")
        pickedMedia = null
    }

    fun pickMedia(media: SelectedMedia) {
        pickedMedia = media
    }

    fun submit(
        uploader: MediaUploader,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        if (isWorking) return
        viewModelScope.launch(Dispatchers.IO) {
            isWorking = true
            try {
                val local = pickedMedia
                if (local != null) {
                    val uploadedUrl = uploadImage(local, uploader, onError)
                    if (uploadedUrl == null) {
                        isWorking = false
                        return@launch
                    }
                    picture.value = TextFieldValue(uploadedUrl)
                    pickedMedia = null
                }

                try {
                    publish()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    onError(
                        loadStringRes(Res.string.read_only_user),
                        loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    isWorking = false
                    return@launch
                } catch (_: SignerExceptions) {
                    // The user rejected it in their signer, or the signer was unavailable: nothing
                    // was published, so keep the form open instead of reporting success.
                    return@launch
                }
                clear()
                onSuccess()
            } finally {
                isWorking = false
            }
        }
    }

    private suspend fun publish() {
        val currentPack = pack
        if (currentPack == null) {
            account.createOwnedEmojiPack(
                title = name.value.text,
                description = description.value.text,
                image = picture.value.text,
            )
        } else {
            account.updateOwnedEmojiPackMetadata(
                dTag = currentPack.identifier,
                newTitle = name.value.text,
                newDescription = description.value.text,
                newImage = picture.value.text,
            )
        }
    }

    fun clear() {
        name.value = TextFieldValue()
        picture.value = TextFieldValue()
        description.value = TextFieldValue()
        pickedMedia = null
    }

    private suspend fun uploadImage(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ): String? = uploadToDefaultServer(galleryUri, account, uploader, onError)
}
