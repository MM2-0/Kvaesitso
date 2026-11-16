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
package com.vitorpamplona.amethyst.ui.components

import android.graphics.drawable.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.asDrawable
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.gif
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.ReportCaptureLoading
import com.vitorpamplona.amethyst.commons.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font10SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size6dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

@Composable
fun GifVideoView(
    videoUri: String,
    contentDescription: String?,
    dimensions: DimensionTag?,
    blurhash: String?,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    thumbhash: String? = null,
) {
    // Pure read path — DimensionTag.aspectRatioOrNull() is a one-line int division and
    // MediaAspectRatioCache.get() is a synchronized LruCache lookup. Wrapping this in
    // remember() to avoid the recompute would cost more (slot read + N equality checks)
    // than the work it saves; that's why this stays as a plain expression.
    val ratio = dimensions?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(videoUri)

    // Mirrors [mediaSizingModifier]: Crop gets fillMaxSize() and a known ratio gets
    // aspectRatio(), both of which bound the height. Everything else is a bare
    // fillMaxWidth() that wraps its content, and only THAT case needs a loading state
    // with intrinsic height to keep the note from collapsing to nothing. Keying the
    // fallback on `ratio` alone would put raw URL text inside every Crop card cell --
    // MyAsyncImage passes dimensions/blurhash/thumbhash all null, so a gif in a card
    // slot hits this on first load, before MediaAspectRatioCache knows its size.
    val heightIsBounded = contentScale == ContentScale.Crop || ratio != null
    val autoPlay = accountViewModel.settings.autoPlayVideos()
    val borderModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier
    val context = LocalContext.current
    val resources = LocalResources.current

    // Share the static-image sizing policy so animated media obeys it too: in Crop
    // contexts (e.g. the multi-image gallery grid) the cell has a fixed width AND height,
    // so the content must fill and crop rather than impose its own aspect ratio, which
    // would overflow the cell and spill over neighbouring content like the reaction row.
    val containerModifier =
        borderModifier
            .then(mediaSizingModifier(ratio, contentScale))
            .let { if (onDialog != null) it.clickable { onDialog() } else it }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = videoUri,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        ) {
            val state by painter.state.collectAsState()

            val successState = state as? AsyncImagePainter.State.Success
            val drawable = successState?.result?.image?.asDrawable(resources)

            LaunchedEffect(autoPlay, drawable) {
                if (drawable is Animatable) {
                    if (autoPlay) drawable.start() else drawable.stop()
                }
            }

            when (state) {
                is AsyncImagePainter.State.Loading -> {
                    ReportCaptureLoading()
                    // When the height is unbounded (see [heightIsBounded]) this branch MUST
                    // emit something with an intrinsic height, or the box wraps nothing and the
                    // whole note collapses to zero -- no picture, no URL, no spinner, just a gap
                    // in the feed until the load finishes. DisplayBlurHash renders NOTHING when
                    // both hashes are absent (placeholderModel returns null), which is exactly
                    // what a no-imeta post hits. Mirrors UrlImageView's ladder.
                    if (blurhash != null || thumbhash != null) {
                        DisplayBlurHash(
                            blurhash,
                            contentDescription,
                            contentScale,
                            Modifier.fillMaxSize(),
                            thumbhash = thumbhash,
                        )
                    } else if (heightIsBounded) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingAnimation(Size40dp, Size6dp)
                        }
                    } else {
                        WaitAndDisplay {
                            DisplayUrlWithLoadingSymbol(videoUri)
                        }
                    }
                }

                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent(Modifier.fillMaxSize())

                    SideEffect {
                        val image = (state as AsyncImagePainter.State.Success).result.image
                        MediaAspectRatioCache.add(videoUri, image.width, image.height)
                    }
                }

                is AsyncImagePainter.State.Error -> {
                    ClickableUrlOrBlossom(urlText = videoUri, url = videoUri)
                }

                else -> {}
            }
        }

        if (!autoPlay) {
            Text(
                text = stringRes(Res.string.gif),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = Font10SP,
                lineHeight = Font10SP,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(SmallBorder)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
