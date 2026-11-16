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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.upload_image
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.actions.NewUserMetadataViewModel
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableImageDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size100dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.userProfileBorderModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.UserExternalIdentitiesViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileHeader(
    baseUser: User,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    externalIdentities: UserExternalIdentitiesViewModel,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Box {
        DrawBanner(baseUser, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 100.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                ProfilePictureWithUploadOverlay(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = Size100dp,
                )

                Row(
                    modifier =
                        Modifier
                            .height(Size35dp)
                            .padding(bottom = 3.dp),
                ) {
                    ProfileActions(baseUser, accountViewModel, nav)
                }
            }

            DrawAdditionalInfo(baseUser, appRecommendations, externalIdentities, accountViewModel, nav)

            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun ZoomableUserPicture(
    baseUser: User,
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var zoomImageUrl by remember { mutableStateOf<String?>(null) }
    val source = rememberZoomSourceAnchor()

    ClickableUserPicture(
        baseUser = baseUser,
        accountViewModel = accountViewModel,
        size = size,
        modifier =
            MaterialTheme.colorScheme.userProfileBorderModifier
                .zoomSourceAnchor(source),
        onClick = {
            val pic = baseUser.profilePicture()
            if (pic != null) {
                zoomImageUrl = pic
            }
        },
        onLongClick = {
            baseUser.profilePicture()?.let { it1 ->
                scope.launch {
                    clipboardManager.setText(it1)
                }
            }
        },
    )

    zoomImageUrl?.let {
        ZoomableImageDialog(
            imageUrl = RichTextParser.parseImageOrVideo(it),
            source = source,
            onDismiss = { zoomImageUrl = null },
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ProfilePictureWithUploadOverlay(
    baseUser: User,
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val isMe by remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }
    val picture by observeUserPicture(baseUser, accountViewModel)

    Box(contentAlignment = Alignment.Center) {
        ZoomableUserPicture(
            baseUser = baseUser,
            accountViewModel = accountViewModel,
            size = size,
        )
        if (isMe && picture.isNullOrBlank()) {
            ProfilePictureUploadButton(accountViewModel, size)
        }
    }
}

@Composable
private fun ProfilePictureUploadButton(
    accountViewModel: AccountViewModel,
    size: Dp,
) {
    val postViewModel: NewUserMetadataViewModel = rememberViewModel { NewUserMetadataViewModel() }
    postViewModel.init(accountViewModel)

    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelectSingle(
            onImageUri = { media ->
                showGallerySelect = false
                if (media != null) {
                    postViewModel.uploadPictureAndSave(media, accountViewModel.mediaUploader, accountViewModel.toastManager::toast)
                }
            },
        )
    }

    if (postViewModel.isUploadingImageForPicture) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            LoadingAnimation()
        }
    } else {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable { showGallerySelect = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.AddPhotoAlternate,
                contentDescription = stringRes(Res.string.upload_image),
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
