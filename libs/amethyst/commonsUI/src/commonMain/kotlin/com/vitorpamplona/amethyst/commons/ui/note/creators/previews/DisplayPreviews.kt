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
package com.vitorpamplona.amethyst.commons.ui.note.creators.previews

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.composer.PreviewState
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.LocalNotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.theme.FillWidthQuoteBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Height100Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SquaredQuoteBorderModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DisplayPreviews(
    state: PreviewState,
    attachments: IMetaAttachments,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PrepareLinkedImageMetadata(state, attachments, accountViewModel)

    val urlPreviews by state.results.collectAsStateWithLifecycle(emptyList())

    if (urlPreviews.isNotEmpty()) {
        Row(HalfHorzPadding) {
            if (urlPreviews.size > 1) {
                LazyRow(Height100Modifier, horizontalArrangement = spacedBy(Size5dp)) {
                    items(urlPreviews) {
                        Box(SquaredQuoteBorderModifier) {
                            PreviewUrl(it, accountViewModel, nav)
                        }
                    }
                }
            } else {
                Box(FillWidthQuoteBorderModifier) {
                    PreviewUrlFillWidth(urlPreviews[0], accountViewModel, nav)
                }
            }
        }
    }
}

/**
 * An image the composer previews from a pasted link gets the same imeta an upload does (hash, size,
 * dimensions, blurhash), so readers can lay it out before it loads. Links without an image
 * extension are classified by the same URL preview that renders them.
 */
@Composable
fun PrepareLinkedImageMetadata(
    state: PreviewState,
    attachments: IMetaAttachments,
    accountViewModel: AccountViewModel,
) {
    val notePlatform = LocalNotePlatform.current
    LaunchedEffect(state, attachments, accountViewModel) {
        val scope = this
        state.results.collect { urls ->
            attachments.prepareLinkedImages(urls, scope, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder) { url ->
                notePlatform.previewMimeType(url, accountViewModel)
            }
        }
    }
}

private suspend fun NotePlatform.previewMimeType(
    url: String,
    accountViewModel: AccountViewModel,
): String? =
    // A platform without a URL previewer never answers.
    withTimeoutOrNull(PREVIEW_TIMEOUT_MS) {
        val result = CompletableDeferred<UrlPreviewState>()
        loadUrlPreview(url, accountViewModel) { result.complete(it) }
        (result.await() as? UrlPreviewState.Loaded)?.previewInfo?.mimeType
    }

private const val PREVIEW_TIMEOUT_MS = 30_000L
