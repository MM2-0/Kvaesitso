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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.theme.RichTextBlockPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

private const val ASPECT_RATIO = 4f / 3f
private const val PORTRAIT_ASPECT_RATIO = 3f / 4f
private val IMAGE_SPACING: Dp = Size5dp

private data class FirstImageOrientation(
    val aspectRatio: Float,
    val isLandscape: Boolean,
)

private fun MediaUrlImage.resolvedAspectRatio(): Float? = dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(url)

private fun MediaUrlImage?.resolveOrientation(
    landscapeDefaultAspectRatio: Float,
    portraitDefaultAspectRatio: Float,
    defaultToLandscape: Boolean = false,
): FirstImageOrientation {
    val ratio = this?.resolvedAspectRatio()
    val isLandscape = ratio?.let { it >= 1f } ?: defaultToLandscape
    val resolvedAspectRatio =
        ratio
            ?.takeIf { it > 0f }
            ?: if (isLandscape) landscapeDefaultAspectRatio else portraitDefaultAspectRatio

    return FirstImageOrientation(
        aspectRatio = resolvedAspectRatio,
        isLandscape = isLandscape,
    )
}

@Composable
private fun rememberFirstImageOrientation(firstImage: MediaUrlImage?): FirstImageOrientation {
    val orientationState =
        remember(firstImage) {
            mutableStateOf(firstImage.resolveOrientation(ASPECT_RATIO, PORTRAIT_ASPECT_RATIO))
        }

    LaunchedEffect(firstImage) {
        if (firstImage == null) return@LaunchedEffect
        if (firstImage.dim?.hasSize() == true) return@LaunchedEffect

        // MediaAspectRatioCache entries are snapshot state, so this suspends until the decoder
        // records the ratio. It used to poll every 200 ms, forever when the ratio never came (images
        // off, a 404, a decode failure), waking the main thread five times a second per gallery.
        val ratio = snapshotFlow { firstImage.resolvedAspectRatio() }.first { it != null && it > 0f } ?: return@LaunchedEffect
        orientationState.value = FirstImageOrientation(ratio, ratio >= 1f)
    }

    return orientationState.value
}

@Composable
private fun GalleryImage(
    image: MediaUrlImage,
    allImages: ImmutableList<MediaUrlImage>,
    modifier: Modifier = Modifier,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = modifier) {
        ZoomableContentView(
            content = image,
            images = allImages,
            roundedCorner = roundedCorner,
            contentScale = contentScale,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ImageGallery(
    images: ImageGalleryParagraph,
    state: RichTextViewerState,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    roundedCorner: Boolean = true,
) {
    if (images.words.isEmpty()) return

    val resolvedImages =
        images.words
            .mapNotNull { segment ->
                val imageUrl = segment.segmentText
                state.mediaForPager[imageUrl] as? MediaUrlImage
            }.toImmutableList()

    Column(modifier = modifier.then(RichTextBlockPadding)) {
        when (resolvedImages.size) {
            1 -> SingleImageGallery(resolvedImages, accountViewModel, roundedCorner)
            2 -> TwoImageGallery(resolvedImages, accountViewModel, roundedCorner)
            3 -> ThreeImageGallery(resolvedImages, accountViewModel, roundedCorner)
            4 -> FourImageGallery(resolvedImages, accountViewModel, roundedCorner)
            else -> ManyImageGallery(resolvedImages, accountViewModel, roundedCorner)
        }
    }
}

@Composable
private fun SingleImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    GalleryImage(
        image = images.first(),
        allImages = images,
        modifier = Modifier.fillMaxWidth(),
        roundedCorner = roundedCorner,
        contentScale = ContentScale.FillWidth,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun TwoImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    val orientation = rememberFirstImageOrientation(images.firstOrNull())

    if (orientation.isLandscape) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
        ) {
            images.take(2).forEach { image ->
                GalleryImage(
                    image = image,
                    allImages = images,
                    modifier = Modifier.fillMaxWidth().aspectRatio(orientation.aspectRatio),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
        ) {
            images.take(2).forEach { image ->
                GalleryImage(
                    image = image,
                    allImages = images,
                    modifier = Modifier.weight(1f, fill = true).aspectRatio(orientation.aspectRatio),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun ThreeImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    val firstImage = images.first()
    val orientation = rememberFirstImageOrientation(firstImage)
    val remainingImages = images.drop(1)

    if (orientation.isLandscape) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
        ) {
            GalleryImage(
                image = firstImage,
                allImages = images,
                modifier = Modifier.fillMaxWidth().aspectRatio(orientation.aspectRatio),
                roundedCorner = roundedCorner,
                contentScale = ContentScale.Crop,
                accountViewModel = accountViewModel,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
            ) {
                remainingImages.forEach { image ->
                    GalleryImage(
                        image = image,
                        allImages = images,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    } else {
        BoxWithConstraints {
            val horizontalSpacing = IMAGE_SPACING
            val columnWidth = ((maxWidth - horizontalSpacing).coerceAtLeast(0.dp)) / 2
            val rowHeight = (columnWidth * 2) + IMAGE_SPACING

            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                GalleryImage(
                    image = firstImage,
                    allImages = images,
                    modifier = Modifier.width(columnWidth).fillMaxHeight(),
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )

                Column(
                    modifier = Modifier.width(columnWidth).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
                ) {
                    remainingImages.forEach { image ->
                        GalleryImage(
                            image = image,
                            allImages = images,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            roundedCorner = roundedCorner,
                            contentScale = ContentScale.Crop,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FourImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    Column(
        modifier = Modifier.aspectRatio(ASPECT_RATIO),
        verticalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
    ) {
        images.chunked(2).forEach { rowImages ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IMAGE_SPACING),
            ) {
                rowImages.forEach { image ->
                    GalleryImage(
                        image = image,
                        allImages = images,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManyImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
    roundedCorner: Boolean,
) {
    val columns =
        when {
            images.size <= 9 -> 3
            else -> 4
        }

    Column(verticalArrangement = Arrangement.spacedBy(Size5dp)) {
        images.chunked(columns).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
            ) {
                rowImages.forEach { image ->
                    GalleryImage(
                        image = image,
                        allImages = images,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
                repeat(columns - rowImages.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
