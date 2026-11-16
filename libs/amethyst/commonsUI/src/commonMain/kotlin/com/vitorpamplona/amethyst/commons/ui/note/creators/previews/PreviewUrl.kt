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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewCard
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.platform.VideoView
import com.vitorpamplona.amethyst.commons.ui.note.platform.rememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.state.produceCachedState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PreviewUrl(
    myUrlPreview: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when {
        RichTextParser.isValidURL(myUrlPreview) -> {
            when {
                RichTextParser.isImageUrl(myUrlPreview) -> {
                    AsyncImage(
                        model = myUrlPreview,
                        contentDescription = myUrlPreview,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                }

                RichTextParser.isVideoUrl(myUrlPreview) -> {
                    VideoView(
                        myUrlPreview,
                        mimeType = null,
                        roundedCorner = false,
                        contentScale = ContentScale.FillHeight,
                        accountViewModel = accountViewModel,
                    )
                }

                else -> {
                    MyLoadUrlPreviewDirect(myUrlPreview, myUrlPreview, accountViewModel, nav)
                }
            }
        }

        RichTextParser.startsWithNIP19Scheme(myUrlPreview) -> {
            val bgColor = MaterialTheme.colorScheme.background
            val backgroundColor = remember { mutableStateOf(bgColor) }

            BechLinkPreview(
                word = myUrlPreview,
                canPreview = true,
                quotesLeft = 1,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        RichTextParser.isUrlWithoutScheme(myUrlPreview) -> {
            MyLoadUrlPreviewDirect("https://$myUrlPreview", myUrlPreview, accountViewModel, nav)
        }
    }
}

@Composable
fun PreviewUrlFillWidth(
    myUrlPreview: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (RichTextParser.isValidURL(myUrlPreview)) {
        when {
            RichTextParser.isImageUrl(myUrlPreview) -> {
                AsyncImage(
                    model = myUrlPreview,
                    contentDescription = myUrlPreview,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                )
            }

            RichTextParser.isVideoUrl(myUrlPreview) -> {
                VideoView(
                    myUrlPreview,
                    mimeType = null,
                    roundedCorner = false,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }

            else -> {
                MyLoadUrlPreviewDirectFillWidth(myUrlPreview, myUrlPreview, accountViewModel, nav)
            }
        }
    } else if (RichTextParser.startsWithNIP19Scheme(myUrlPreview)) {
        val bgColor = MaterialTheme.colorScheme.background
        val backgroundColor = remember { mutableStateOf(bgColor) }

        BechLinkPreview(
            word = myUrlPreview,
            canPreview = true,
            quotesLeft = 1,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else if (RichTextParser.isUrlWithoutScheme(myUrlPreview)) {
        MyLoadUrlPreviewDirectFillWidth("https://$myUrlPreview", myUrlPreview, accountViewModel, nav)
    }
}

@Composable
private fun BechLinkPreview(
    word: String,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val loadedLink by produceCachedState(cache = accountViewModel.bechLinkCache, key = word)

    val baseNote = loadedLink?.baseNote

    if (canPreview && quotesLeft > 0 && baseNote != null) {
        Row {
            NoteCompose(
                baseNote = baseNote,
                modifier = Modifier.aspectRatio(1f),
                isQuotedNote = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else {
        val text =
            if (word.length > 16) {
                word.replaceRange(8, word.length - 8, ":")
            } else {
                word
            }

        Text(text = text, maxLines = 1)
    }
}

@Composable
private fun MyLoadUrlPreviewDirect(
    url: String,
    urlText: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val urlPreviewState = rememberUrlPreviewState(url, accountViewModel)

    CrossfadeIfEnabled(
        targetState = urlPreviewState,
        label = "UrlPreview",
    ) { state ->
        when (state) {
            is UrlPreviewState.Loaded -> {
                if (state.previewInfo.mimeType.startsWith("image")) {
                    AsyncImage(
                        model = state.previewInfo.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    )
                } else if (state.previewInfo.mimeType.startsWith("video") || state.previewInfo.mimeType.startsWith("audio")) {
                    VideoView(
                        state.previewInfo.url,
                        mimeType = state.previewInfo.mimeType,
                        roundedCorner = false,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.aspectRatio(1f)) {
                        // A page with no OpenGraph image still has its icon: shown whole, not cropped.
                        val hasImage = state.previewInfo.imageUrlFullPath.isNotBlank()
                        AsyncImage(
                            model = if (hasImage) state.previewInfo.imageUrlFullPath else state.previewInfo.iconUrlFullPath,
                            contentDescription = state.previewInfo.title,
                            contentScale = if (hasImage) ContentScale.Crop else ContentScale.Fit,
                            modifier = if (hasImage) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(20.dp),
                        )

                        Text(
                            text = state.previewInfo.verifiedHost ?: state.previewInfo.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            else -> {
                Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.aspectRatio(1f)) {
                    ClickableUrlOrBlossom(urlText, url)
                }
            }
        }
    }
}

@Composable
private fun MyLoadUrlPreviewDirectFillWidth(
    url: String,
    urlText: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val urlPreviewState = rememberUrlPreviewState(url, accountViewModel)

    CrossfadeIfEnabled(
        targetState = urlPreviewState,
        label = "UrlPreview",
    ) { state ->
        when (state) {
            is UrlPreviewState.Loaded -> {
                if (state.previewInfo.mimeType.startsWith("image")) {
                    AsyncImage(
                        model = state.previewInfo.url,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (state.previewInfo.mimeType.startsWith("video") || state.previewInfo.mimeType.startsWith("audio")) {
                    VideoView(
                        state.previewInfo.url,
                        mimeType = state.previewInfo.mimeType,
                        roundedCorner = false,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    UrlPreviewCard(
                        url,
                        previewInfo = state.previewInfo,
                        onUrlComments = { nav.nav(Route.Url(url)) },
                    )
                }
            }

            is UrlPreviewState.Loading -> {
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
