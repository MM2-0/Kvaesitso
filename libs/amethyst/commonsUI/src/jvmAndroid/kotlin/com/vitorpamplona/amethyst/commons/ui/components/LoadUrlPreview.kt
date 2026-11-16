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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.theme.RichTextBlockPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun LoadUrlPreview(
    url: String,
    urlText: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    if (LocalMediaCapture.current == null && !accountViewModel.settings.showUrlPreview()) {
        ClickableUrlOrBlossom(urlText, url)
    } else {
        LoadUrlPreviewDirect(url, urlText, callbackUri, accountViewModel, nav)
    }
}

@Composable
fun rememberUrlPreviewState(
    url: String,
    accountViewModel: AccountViewModel,
): UrlPreviewState {
    @Suppress("ProduceStateDoesNotAssignValue")
    val urlPreviewState by
        produceState(
            initialValue = UrlCachedPreviewer.cache.get(url) ?: UrlPreviewState.Loading,
            key1 = url,
        ) {
            if (value == UrlPreviewState.Loading) {
                accountViewModel.urlPreview(url) { value = it }
            }
        }
    return urlPreviewState
}

@Composable
fun LoadUrlPreviewDirect(
    url: String,
    urlText: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    val urlPreviewState = rememberUrlPreviewState(url, accountViewModel)

    CrossfadeIfEnabled(
        targetState = urlPreviewState,
        label = "UrlPreview",
    ) { state ->
        when (state) {
            is UrlPreviewState.Loaded -> {
                RenderLoaded(state, url, callbackUri, accountViewModel, nav)
            }

            is UrlPreviewState.Loading -> {
                ReportCaptureLoading()
                WaitAndDisplay {
                    DisplayUrlWithLoadingSymbol(url, accountViewModel.toastManager::toast)
                }
            }

            else -> {
                ClickableUrlOrBlossom(urlText, url)
            }
        }
    }
}

@Composable
fun RenderLoaded(
    state: UrlPreviewState.Loaded,
    url: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    // Bound to a local: `UrlInfoItem` lives in another module, so the null check below cannot
    // smart-cast the property itself.
    val playableMedia = state.previewInfo.playableMediaUrl

    when {
        state.previewInfo.mimeType.startsWith("image") -> {
            BleedingMedia(RichTextBlockPadding) { roundedCorner ->
                ZoomableContentView(
                    content = MediaUrlImage(url, uri = callbackUri),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }

        // Audio rides the video pipeline; see RichTextParser.videoExt. The fetched Content-Type is
        // forwarded because it is the only type signal for an extension-less URL -- an HLS
        // playlist served as `audio/x-mpegurl` off `/stream?id=1` is otherwise handed to the
        // progressive source and fails.
        state.previewInfo.mimeType.startsWith("video") || state.previewInfo.mimeType.startsWith("audio") -> {
            BleedingMedia(RichTextBlockPadding) { roundedCorner ->
                ZoomableContentView(
                    content = MediaUrlVideo(url, uri = callbackUri, mimeType = state.previewInfo.mimeType),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }

        state.previewInfo.mimeType.startsWith("application/pdf") -> {
            Box(modifier = RichTextBlockPadding) {
                ZoomableContentView(
                    content = MediaUrlPdf(url, uri = callbackUri, mimeType = state.previewInfo.mimeType),
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }

        // An HTML page that declares the media file it is a player for: nostr.build publishes one
        // such page per upload and names the real file in `og:video` or `og:audio`. Play that
        // file, with the page's `og:image` as poster/cover art, instead of a card that only links
        // out. `playableMediaUrl` is null unless the page's declared type held up, so a page whose
        // `og:video` is really an embed (YouTube's, say) still falls through to the card below.
        playableMedia != null -> {
            BleedingMedia(RichTextBlockPadding) { roundedCorner ->
                ZoomableContentView(
                    content =
                        MediaUrlVideo(
                            url = playableMedia,
                            description = state.previewInfo.title.ifBlank { null },
                            uri = callbackUri,
                            artworkUri = state.previewInfo.imageUrlFullPath.ifBlank { null },
                            mimeType = state.previewInfo.playableMediaType,
                        ),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }

        else -> {
            UrlPreviewCard(
                url,
                state.previewInfo,
                onUrlComments =
                    nav?.let {
                        { it.nav(Route.Url(url)) }
                    },
            )
        }
    }
}
