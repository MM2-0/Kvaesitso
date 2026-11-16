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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserBanner
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.resources.profile_banner
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleHeaderImage
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.authorNotePictureForImageHeader
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun DefaultImageHeader(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = SimpleHeaderImage,
) {
    WatchAuthor(baseNote = note, accountViewModel) {
        Box {
            BannerImage(it, modifier, accountViewModel)

            Box(authorNotePictureForImageHeader.align(Alignment.BottomStart)) {
                BaseUserPicture(it, Size55dp, accountViewModel, Modifier)
            }
        }
    }
}

@Composable
fun DefaultImageBanner(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = SimpleHeaderImage,
) {
    WatchAuthor(baseNote = note, accountViewModel) {
        BannerImage(it, modifier, accountViewModel)
    }
}

@Composable
fun DefaultImageHeaderBackground(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = SimpleHeaderImage,
) {
    WatchAuthor(baseNote = note, accountViewModel) {
        Box {
            BannerImage(it, modifier.blur(Size16dp), accountViewModel)

            Box(authorNotePictureForImageHeader.align(Alignment.BottomStart)) {
                BaseUserPicture(it, Size55dp, accountViewModel, Modifier)
            }
        }
    }
}

@Composable
fun DefaultImageBannerBackground(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = SimpleHeaderImage,
) {
    WatchAuthor(baseNote = note, accountViewModel) {
        BannerImage(it, modifier.blur(Size16dp), accountViewModel)
    }
}

@Composable
fun BannerImage(
    author: User,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    val banner by observeUserBanner(author, accountViewModel)

    BannerImage(banner, modifier, accountViewModel)
}

@Composable
fun BannerImage(
    banner: String?,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    if (!banner.isNullOrBlank()) {
        MyAsyncImage(
            imageUrl = banner,
            contentDescription = stringRes(Res.string.preview_card_image_for, banner),
            contentScale = ContentScale.Crop,
            mainImageModifier = Modifier,
            loadedImageModifier = modifier,
            accountViewModel = accountViewModel,
            onLoadingBackground = {
                DefaultProfileBanner(modifier, 4)
            },
            onError = {
                DefaultProfileBanner(modifier, 4)
            },
        )
    } else {
        DefaultProfileBanner(modifier, 5)
    }
}

@Composable
fun DefaultProfileBanner(
    modifier: Modifier,
    sizeReference: Int,
) {
    Image(
        painter = painterRes(Res.drawable.profile_banner, sizeReference),
        contentDescription = stringRes(Res.string.profile_banner),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
