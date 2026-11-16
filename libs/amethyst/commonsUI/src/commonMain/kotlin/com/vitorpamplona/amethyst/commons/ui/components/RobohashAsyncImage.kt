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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedMediaUrl
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomCacheHeaders
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.onBackgroundColorFilter
import coil3.Image as CoilImage

/**
 * Plays or pauses an animated avatar (GIF/AVIF) once Coil has decoded it. Android drives the
 * decoded `Animatable` drawable; desktop decodes only the first frame, so there [autoPlay] has
 * no effect.
 */
@Composable
internal expect fun AnimatedImageAutoPlay(
    image: CoilImage?,
    autoPlay: Boolean,
)

@Composable
fun RobohashAsyncImage(
    robot: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadRobohash: Boolean,
) {
    if (loadRobohash) {
        Image(
            imageVector = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    } else {
        Image(
            painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
            contentDescription = contentDescription,
            colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
            modifier = modifier,
        )
    }
}

@Composable
fun RobohashFallbackAsyncImage(
    robot: String,
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean = true,
    onError: (() -> Unit)? = null,
) {
    val useThumbnailCache = LocalProfilePictureCache.current
    if (model != null && loadProfilePicture && isAnimatedMediaUrl(model)) {
        GifProfilePicture(
            userHex = robot,
            userPicture = model,
            contentDescription = contentDescription,
            modifier = modifier,
            loadRobohash = loadRobohash,
            autoPlay = autoPlayGif,
            onError = onError,
        )
    } else if (model != null && loadProfilePicture) {
        val fallbackPainter =
            if (loadRobohash) {
                rememberVectorPainter(
                    image = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
                )
            } else {
                forwardingPainter(
                    painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                    colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                )
            }

        SubcomposeAsyncImage(
            // ProfilePictureUrl only loads where the host registered its thumbnail-cache fetcher
            // (see LocalProfilePictureCache). That fetcher delegates to Coil's http-only
            // NetworkFetcher, so a LOCAL model (e.g. a decrypted Concord community icon cached at
            // file://) would fail there. Route only remote http(s) pictures through the thumbnail
            // cache; hand local/content URIs to Coil's native fetchers, which load them directly.
            model =
                if (useThumbnailCache && (model.startsWith("http://", ignoreCase = true) || model.startsWith("https://", ignoreCase = true))) {
                    ProfilePictureUrl(model)
                } else {
                    model
                },
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
            onState = onError?.let { onError -> { if (it is AsyncImagePainter.State.Error) onError() } },
        ) {
            val state by painter.state.collectAsState()
            AnimatedImageAutoPlay((state as? AsyncImagePainter.State.Success)?.result?.image, autoPlayGif)

            when (state) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                ->
                    Image(
                        painter = fallbackPainter,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        alignment = alignment,
                        contentScale = contentScale,
                        alpha = alpha,
                        colorFilter = colorFilter,
                    )
                else -> {}
            }
        }
    } else {
        if (loadRobohash) {
            Image(
                imageVector = CachedRobohash.get(robot, MaterialTheme.colorScheme.isLight),
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )
        } else {
            Image(
                painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                contentDescription = contentDescription,
                colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
            )
        }
    }
}

@Composable
fun GifProfilePicture(
    userHex: String,
    userPicture: String,
    contentDescription: String?,
    modifier: Modifier,
    loadRobohash: Boolean,
    autoPlay: Boolean,
    onError: (() -> Unit)? = null,
) {
    val fallbackPainter =
        if (loadRobohash) {
            rememberVectorPainter(image = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight))
        } else {
            forwardingPainter(
                painter = rememberMaterialSymbolPainter(MaterialSymbols.Face),
                colorFilter = MaterialTheme.colorScheme.onBackgroundColorFilter,
            )
        }

    val context = LocalPlatformContext.current
    val markForLocalBlossom = LocalProfilePictureCache.current
    // Animated avatars skip ProfilePictureFetcher (its thumbnail cache would flatten them), so
    // they carry the profile-picture marker themselves for the local Blossom cache bridge. Only
    // hosts that install that bridge (LocalProfilePictureCache) strip the marker before the
    // request leaves; anywhere else it would reach the image server, so it is not added.
    val model: Any =
        remember(userPicture, markForLocalBlossom) {
            if (markForLocalBlossom) {
                ImageRequest
                    .Builder(context)
                    .data(userPicture)
                    .httpHeaders(
                        NetworkHeaders
                            .Builder()
                            .set(LocalBlossomCacheHeaders.MEDIA_HEADER, LocalBlossomCacheHeaders.PROFILE_PICTURE)
                            .build(),
                    ).build()
            } else {
                userPicture
            }
        }

    Box(modifier = modifier) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = onError?.let { onError -> { if (it is AsyncImagePainter.State.Error) onError() } },
        ) {
            val state by painter.state.collectAsState()
            AnimatedImageAutoPlay((state as? AsyncImagePainter.State.Success)?.result?.image, autoPlay)

            when (state) {
                is AsyncImagePainter.State.Success -> {
                    SubcomposeAsyncImageContent()
                }

                else -> {
                    Image(
                        painter = fallbackPainter,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}
