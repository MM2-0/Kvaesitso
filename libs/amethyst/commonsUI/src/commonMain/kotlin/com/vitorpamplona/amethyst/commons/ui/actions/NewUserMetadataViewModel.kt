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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.toPictureMeta
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServerWithMetadata
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import com.vitorpamplona.quartz.nip68Picture.PictureMeta

class NewUserMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    val name = mutableStateOf("")
    val displayName = mutableStateOf("")
    val about = mutableStateOf("")

    val picture = mutableStateOf("")
    val banner = mutableStateOf("")

    // NIP-92 imeta of the images uploaded in this session. Dropped on save if the user then
    // replaces the URL by hand, since an imeta only describes the exact URL it names.
    private var pictureMeta: PictureMeta? = null
    private var bannerMeta: PictureMeta? = null

    val website = mutableStateOf("")
    val pronouns = mutableStateOf("")
    val nip05 = mutableStateOf("")
    val lnAddress = mutableStateOf("")
    val lnURL = mutableStateOf("")
    val clinkOffer = mutableStateOf("")

    val twitter = mutableStateOf("")
    val github = mutableStateOf("")
    val mastodon = mutableStateOf("")

    var isUploadingImageForPicture by mutableStateOf(false)
    var isUploadingImageForBanner by mutableStateOf(false)

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() {
        account.userProfile().metadataOrNull()?.flow?.value?.let {
            name.value = it.info.name ?: ""
            displayName.value = it.info.displayName ?: ""
            about.value = it.info.about ?: ""
            picture.value = it.info.picture ?: ""
            banner.value = it.info.banner ?: ""
            website.value = it.info.website ?: ""
            pronouns.value = it.info.pronouns ?: ""
            nip05.value = it.info.nip05 ?: ""
            lnAddress.value = it.info.lud16 ?: ""
            lnURL.value = it.info.lud06 ?: ""
            clinkOffer.value = it.info.clinkOffer ?: ""
        }

        twitter.value = ""
        github.value = ""
        mastodon.value = ""

        // Load identities from kind 10011 first, fall back to kind 0 for backwards compat
        val identities =
            account.userMetadata.getExternalIdentitiesEvent()?.identityClaims()
                ?: account
                    .userProfile()
                    .metadataOrNull()
                    ?.flow
                    ?.value
                    ?.identities
                ?: emptyList()

        identities.forEach { identity ->
            when (identity) {
                is TwitterIdentity -> twitter.value = identity.toProofUrl()
                is GitHubIdentity -> github.value = identity.toProofUrl()
                is MastodonIdentity -> mastodon.value = identity.toProofUrl()
            }
        }
    }

    suspend fun create() {
        val metadata =
            account.userMetadata.sendNewUserMetadata(
                name = name.value,
                displayName = displayName.value,
                picture = picture.value,
                banner = banner.value,
                website = website.value,
                pronouns = pronouns.value,
                about = about.value,
                nip05 = nip05.value,
                lnAddress = lnAddress.value,
                lnURL = lnURL.value,
                clinkOffer = clinkOffer.value,
                pictureMeta = pictureMeta,
                bannerMeta = bannerMeta,
            )

        val identities =
            account.userMetadata.sendNewUserIdentities(
                twitter = twitter.value,
                mastodon = mastodon.value,
                github = github.value,
            )

        account.sendLiterallyEverywhere(metadata)
        account.sendLiterallyEverywhere(identities)

        clear()
    }

    fun clear() {
        name.value = ""
        displayName.value = ""
        about.value = ""
        picture.value = ""
        banner.value = ""
        pictureMeta = null
        bannerMeta = null
        website.value = ""
        nip05.value = ""
        lnAddress.value = ""
        lnURL.value = ""
        clinkOffer.value = ""
        twitter.value = ""
        github.value = ""
        mastodon.value = ""
    }

    fun uploadForPicture(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            upload(
                galleryUri = uri,
                uploader = uploader,
                onError = onError,
                setUploading = { isUploadingImageForPicture = it },
            )?.let {
                picture.value = it.url
                pictureMeta = it.toPictureMeta()
            }
        }
    }

    fun uploadForBanner(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            upload(
                galleryUri = uri,
                uploader = uploader,
                onError = onError,
                setUploading = { isUploadingImageForBanner = it },
            )?.let {
                banner.value = it.url
                bannerMeta = it.toPictureMeta()
            }
        }
    }

    fun uploadPictureAndSave(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
    ) {
        load()
        accountViewModel.launchSigner {
            upload(
                galleryUri = uri,
                uploader = uploader,
                onError = onError,
                setUploading = { isUploadingImageForPicture = it },
            )?.let {
                picture.value = it.url
                pictureMeta = it.toPictureMeta()
            }

            create()
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onError: (String, String) -> Unit,
        setUploading: (Boolean) -> Unit,
    ): UploadOrchestrator.OrchestratorResult.ServerResult? {
        setUploading(true)
        return try {
            uploadToDefaultServerWithMetadata(galleryUri, account, uploader, onError)
        } finally {
            setUploading(false)
        }
    }
}
