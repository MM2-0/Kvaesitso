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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedGifUrl
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.rememberFallbackUrlState
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultProfileBanner
import com.vitorpamplona.amethyst.commons.ui.note.platform.AnimatedImage
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableImageDialog
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawBanner(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val userInfo by observeUserInfo(baseUser, accountViewModel)

    DrawBanner(userInfo?.info?.banner, userInfo?.imageMetas?.banner, accountViewModel)
}

/**
 * Draws the profile [banner]. Its NIP-92 [bannerMeta], when the profile declares one, supplies a
 * blurhash to show while it loads and the `fallback` URLs to try when it fails.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawBanner(
    banner: String?,
    bannerMeta: PictureMeta?,
    accountViewModel: AccountViewModel,
) {
    if (!banner.isNullOrBlank()) {
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        var zoomImageDialogOpen by remember { mutableStateOf(false) }
        val source = rememberZoomSourceAnchor()

        val bannerUrl = rememberFallbackUrlState(banner, bannerMeta?.fallback ?: emptyList())
        // Whichever URL is on screen: the primary, or the fallback that replaced it.
        val shownBanner = bannerUrl.url ?: banner
        val hasPreviewHash = !bannerMeta?.blurhash.isNullOrBlank() || !bannerMeta?.thumbhash.isNullOrBlank()

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .zoomSourceAnchor(source)
                    .combinedClickable(
                        onClick = { zoomImageDialogOpen = true },
                        onLongClick = {
                            scope.launch {
                                clipboardManager.setText(shownBanner)
                            }
                        },
                    ),
        ) {
            if (bannerMeta != null && hasPreviewHash) {
                DisplayBlurHash(
                    blurhash = bannerMeta.blurhash,
                    thumbhash = bannerMeta.thumbhash,
                    description = bannerMeta.alt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }

            val loadingUrl = bannerUrl.url
            if (loadingUrl != null && isAnimatedGifUrl(loadingUrl)) {
                // A GIF banner plays like one: looping, silent, no controls. The image loader would
                // only draw its first frame on desktop.
                if (!hasPreviewHash) DefaultProfileBanner(Modifier.matchParentSize(), 1)
                AnimatedImage(
                    url = loadingUrl,
                    contentDescription = bannerMeta?.alt ?: stringRes(id = Res.string.profile_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = { bannerUrl.onError() },
                )
            } else {
                AsyncImage(
                    model = loadingUrl,
                    contentDescription = bannerMeta?.alt ?: stringRes(id = Res.string.profile_image),
                    contentScale = ContentScale.Crop,
                    placeholder = if (hasPreviewHash) null else painterRes(Res.drawable.profile_banner, 1),
                    error = painterRes(Res.drawable.profile_banner, 1),
                    onError = { bannerUrl.onError() },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        if (zoomImageDialogOpen) {
            ZoomableImageDialog(
                imageUrl = RichTextParser.parseImageOrVideo(shownBanner),
                source = source,
                onDismiss = { zoomImageDialogOpen = false },
                accountViewModel = accountViewModel,
            )
        }
    } else {
        Image(
            painter = painterRes(Res.drawable.profile_banner, 2),
            contentDescription = stringRes(id = Res.string.profile_banner),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp),
        )
    }
}
