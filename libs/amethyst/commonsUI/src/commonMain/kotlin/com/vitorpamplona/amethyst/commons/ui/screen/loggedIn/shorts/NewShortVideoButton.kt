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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.shorts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ic_compose
import com.vitorpamplona.amethyst.commons.resources.new_short_video
import com.vitorpamplona.amethyst.commons.resources.record_a_video
import com.vitorpamplona.amethyst.commons.resources.upload_media
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.NewMediaModel
import com.vitorpamplona.amethyst.commons.ui.actions.NewMediaView
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelect
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.TakeVideo
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.canCaptureFromCamera
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Shorts feed's composer. Every *video* posted from here is a NIP-71 kind-22 short — recorded,
 * picked or shared alike — so landscape footage lands in the Shorts feed too instead of being
 * routed to kind 21 by its orientation. Images are unaffected: the gallery picker accepts them and
 * they still post as NIP-68 kind-20 pictures, which the Shorts feed does not read.
 *
 * @param sharedAttachments content URIs handed over by the "New Short" share target. When non-empty
 *   the composer opens on them right away.
 * @param sharedCaption text Android sent alongside the files; pre-fills the caption field.
 */
@Composable
fun NewShortVideoButton(
    accountViewModel: AccountViewModel,
    nav: INav,
    navScrollToTop: () -> Unit,
    sharedAttachments: List<String> = emptyList(),
    sharedCaption: String? = null,
) {
    var isOpen by remember { mutableStateOf(false) }
    var wantsToRecordVideo by remember { mutableStateOf(false) }
    var wantsToPostFromGallery by remember { mutableStateOf(false) }
    var pickedURIs by remember { mutableStateOf<ImmutableList<SelectedMedia>>(persistentListOf()) }

    val scope = rememberCoroutineScope()
    val postViewModel: NewMediaModel = rememberViewModel { NewMediaModel() }
    postViewModel.onceUploaded {
        scope.launch(Dispatchers.IO) {
            delay(500)
            withContext(Dispatchers.Main) { navScrollToTop() }
        }
    }

    val mediaResolver = rememberSharedMediaResolver()
    LaunchedEffect(sharedAttachments) {
        mediaResolver.resolve(sharedAttachments).takeIf { it.isNotEmpty() }?.let { pickedURIs = it }
    }

    if (wantsToRecordVideo) {
        TakeVideo { uri ->
            wantsToRecordVideo = false
            pickedURIs = uri
        }
    }

    if (wantsToPostFromGallery) {
        GallerySelect(
            onImageUri = { uri ->
                wantsToPostFromGallery = false
                pickedURIs = uri
            },
        )
    }

    if (pickedURIs.isNotEmpty()) {
        NewMediaView(
            uris = pickedURIs,
            onClose = { pickedURIs = persistentListOf() },
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
            videoKind = VideoPostKind.SHORT,
            initialCaption = sharedCaption.orEmpty(),
        )
    }

    Column {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Column {
                FloatingActionButton(
                    onClick = {
                        wantsToRecordVideo = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Videocam,
                        contentDescription = stringRes(id = Res.string.record_a_video),
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                FloatingActionButton(
                    onClick = {
                        wantsToPostFromGallery = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        symbol = MaterialSymbols.AddPhotoAlternate,
                        contentDescription = stringRes(id = Res.string.upload_media),
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        FloatingActionButton(
            // With no camera (desktop), the gallery is the only choice: open it instead of a one-item menu.
            onClick = { if (canCaptureFromCamera) isOpen = !isOpen else wantsToPostFromGallery = true },
            modifier = Size55Modifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(id = Res.string.new_short_video),
                    modifier = Size26Modifier,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }

            AnimatedVisibility(
                visible = !isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    painter = painterRes(Res.drawable.ic_compose, 5),
                    contentDescription = stringRes(id = Res.string.new_short_video),
                    modifier = Size26Modifier,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
