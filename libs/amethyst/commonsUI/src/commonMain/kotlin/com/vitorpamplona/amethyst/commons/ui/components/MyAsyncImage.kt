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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedMediaUrl
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.note.platform.GifVideoView
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size6dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size75dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun MyAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    accountViewModel: AccountViewModel,
    onLoadingBackground: (@Composable () -> Unit)?,
    onError: (@Composable () -> Unit)?,
) {
    if (isAnimatedMediaUrl(imageUrl)) {
        // Same slot as a still: the caller's modifiers size and shape it (a banner, a thumbnail),
        // so the GIF neither spills out of its slot nor picks up corners the still would not have.
        Box(mainImageModifier) {
            Box(loadedImageModifier) {
                GifVideoView(
                    videoUri = imageUrl,
                    contentDescription = contentDescription,
                    dimensions = null,
                    blurhash = null,
                    roundedCorner = false,
                    contentScale = contentScale,
                    onDialog = null,
                    accountViewModel = accountViewModel,
                    thumbhash = null,
                )
            }
        }
    } else {
        val ratio = MediaAspectRatioCache.get(imageUrl)
        val capture = LocalMediaCapture.current
        val showImage = remember { mutableStateOf(capture != null || accountViewModel.settings.showImages()) }

        CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center) {
            if (it) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = mainImageModifier,
                ) {
                    val state by painter.state.collectAsState()
                    when (state) {
                        is AsyncImagePainter.State.Loading -> {
                            ReportCaptureLoading()
                            if (ratio != null) {
                                Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                    LoadingAnimation(Size40dp, Size6dp)
                                }
                            } else {
                                WaitAndDisplay {
                                    if (onLoadingBackground != null) {
                                        Box(loadedImageModifier, contentAlignment = Alignment.Center) {
                                            onLoadingBackground()
                                            LoadingAnimation(Size40dp, Size6dp)
                                        }
                                    } else {
                                        DisplayUrlWithLoadingSymbol(imageUrl, accountViewModel.toastManager::toast)
                                    }
                                }
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            if (onError != null) {
                                if (ratio != null) {
                                    Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                        onError()
                                    }
                                } else {
                                    Box(loadedImageModifier, contentAlignment = Alignment.Center) {
                                        onError()
                                    }
                                }
                            } else {
                                ClickableUrlOrBlossom(urlText = imageUrl, url = imageUrl)
                            }
                        }

                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent(loadedImageModifier)

                            SideEffect {
                                val drawable = (state as AsyncImagePainter.State.Success).result.image
                                MediaAspectRatioCache.add(imageUrl, drawable.width, drawable.height)
                            }
                        }

                        else -> {}
                    }
                }
            } else {
                // A cover or banner carries no declared size: only a held-back one asks the host.
                val size = rememberMediaSize(imageUrl, MediaKind.IMAGE, accountViewModel)
                Box(loadedImageModifier.aspectRatio(ratio ?: (16 / 9.0f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            modifier = Modifier.size(Size75dp),
                            onClick = { showImage.value = true },
                        ) {
                            DownloadForOfflineIcon(Size75dp, MaterialTheme.colorScheme.onBackground)
                        }
                        MediaSizeLabel(MediaKind.IMAGE, size)
                    }
                }
            }
        }
    }
}
