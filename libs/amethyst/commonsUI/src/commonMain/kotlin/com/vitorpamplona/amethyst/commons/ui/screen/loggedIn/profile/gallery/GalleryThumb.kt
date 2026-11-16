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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.isHlsMimeType
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.IconWithSizeLabel
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.mediaKind
import com.vitorpamplona.amethyst.commons.ui.components.rememberMediaSize
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent

@Composable
fun GalleryThumbnail(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(baseNote, accountViewModel)
    val noteEvent = noteState.note.event ?: return

    val content =
        when {
            // We don't want to show the hash banner on a thumbnail.
            noteEvent is ProfileGalleryEntryEvent -> noteEvent.toMediaContents(withHash = false, uri = null)

            noteEvent is PictureEvent -> {
                noteEvent.imetaTags().map { imeta ->
                    MediaUrlImage(
                        url = imeta.url,
                        description = noteEvent.content,
                        // We don't want to show the hash banner here
                        hash = null,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = null,
                        mimeType = imeta.mimeType,
                        thumbhash = imeta.thumbhash,
                    )
                }
            }

            noteEvent is VideoEvent -> {
                // An HLS publish writes one imeta per rendition (master + each variant) on the same
                // NIP-71 event. Rendering them all expands a single video into a sub-grid of black
                // tiles inside one gallery card — the .m3u8 playlist is a text manifest Coil can't
                // decode. Collapse to a single tile when every imeta is an HLS playlist; prefer an
                // imeta that carries a poster image, breaking ties by smallest dimensions so we
                // pick the lowest-resolution variant. Non-HLS multi-imeta videos keep the existing
                // per-imeta layout.
                val imetas = noteEvent.imetaTags()
                val isAllHls = imetas.isNotEmpty() && imetas.all { isHlsMimeType(it.mimeType) }
                val toRender =
                    if (isAllHls) {
                        val withPoster = imetas.filter { it.image.isNotEmpty() }
                        val pick =
                            withPoster.ifEmpty { imetas }.minBy {
                                it.dimension?.let { d -> d.width * d.height } ?: Int.MAX_VALUE
                            }
                        listOf(pick)
                    } else {
                        imetas
                    }
                toRender.map { imeta ->
                    MediaUrlVideo(
                        url = imeta.url,
                        description = noteEvent.content,
                        // We don't want to show the hash banner here
                        hash = null,
                        blurhash = imeta.blurhash,
                        dim = imeta.dimension,
                        uri = null,
                        mimeType = imeta.mimeType,
                        thumbhash = imeta.thumbhash,
                        artworkUri = imeta.image.firstOrNull(),
                    )
                }
            }

            noteEvent is LiveActivitiesClipEvent -> {
                noteEvent.videoUrl()?.let { url ->
                    listOf(
                        MediaUrlVideo(
                            url = url,
                            description = noteEvent.title() ?: noteEvent.content,
                            hash = null,
                            blurhash = null,
                            dim = null,
                            uri = null,
                            mimeType = null,
                            thumbhash = null,
                        ),
                    )
                } ?: emptyList()
            }

            else -> {
                emptyList()
            }
        }

    InnerRenderGalleryThumb(content, baseNote, accountViewModel)
}

@Composable
fun InnerRenderGalleryThumb(
    content: List<MediaUrlContent>,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    if (content.isNotEmpty()) {
        GalleryContentView(content, accountViewModel)
    } else {
        DisplayGalleryAuthorBanner(note, accountViewModel)
    }
}

@Composable
fun DisplayGalleryAuthorBanner(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    WatchAuthor(note, accountViewModel) { author ->
        BannerImage(
            author,
            Modifier
                .fillMaxSize()
                .clip(QuoteBorder),
            accountViewModel,
        )
    }
}

@Composable
fun GalleryContentView(
    contentList: List<MediaUrlContent>,
    accountViewModel: AccountViewModel,
) {
    AutoNonlazyGrid(contentList.size, modifier = Modifier.fillMaxSize()) { contentIndex ->
        val content = contentList[contentIndex]

        val sensitivityReason =
            when (content) {
                is MediaUrlVideo -> content.contentWarning
                is MediaUrlImage -> content.contentWarning
                else -> null
            }

        SensitivityWarning(sensitivityReason, accountViewModel) {
            UrlImageView(content, accountViewModel)
        }
    }
}

@Composable
fun UrlImageView(
    content: MediaUrlContent,
    accountViewModel: AccountViewModel,
) {
    val defaultModifier = Modifier.fillMaxSize()

    val showImage =
        remember {
            mutableStateOf(
                accountViewModel.settings.showImages(),
            )
        }

    val isVideo = content is MediaUrlVideo
    val artworkUri = (content as? MediaUrlVideo)?.artworkUri
    // Coil's VideoFrameDecoder can extract a frame from .mp4/.webm but not from an HLS .m3u8
    // playlist (it's a text manifest). For an HLS video without a separate artwork URL, sending
    // the playlist to SubcomposeAsyncImage just produces an Error state and a stand-in icon.
    // Skip the fetch in that case and render blurhash + play overlay directly.
    val imageModelUrl = artworkUri ?: content.url
    val canLoadAsImage = !isVideo || artworkUri != null || !isHlsMedia(content.url, content.mimeType)

    CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center) {
        if (it && canLoadAsImage) {
            SubcomposeAsyncImage(
                model = imageModelUrl,
                contentDescription = content.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading,
                    -> {
                        if (content.blurhash != null || content.thumbhash != null) {
                            DisplayBlurHash(
                                content.blurhash,
                                content.description,
                                ContentScale.Crop,
                                defaultModifier,
                                thumbhash = content.thumbhash,
                            )
                        } else {
                            Box(defaultModifier, contentAlignment = Alignment.Center) {
                                LoadingAnimation()
                            }
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        VideoPlaceholder(content, defaultModifier)
                    }

                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent(defaultModifier)
                        if (isVideo) {
                            Icon(
                                symbol = MaterialSymbols.PlayCircleOutline,
                                contentDescription = stringRes(id = Res.string.play),
                                modifier = Size50Modifier,
                                tint = Color.White,
                            )
                        }
                    }

                    else -> {}
                }
            }
        } else if (it && isVideo) {
            VideoPlaceholder(content, defaultModifier)
        } else {
            if (content.blurhash != null || content.thumbhash != null) {
                DisplayBlurHash(
                    content.blurhash,
                    content.description,
                    ContentScale.Crop,
                    defaultModifier.clickable { showImage.value = true },
                    thumbhash = content.thumbhash,
                )
            }
            // A grid tile has no room for the type word: the size alone, under the icon.
            val size = rememberMediaSize(content, accountViewModel)
            IconWithSizeLabel(content.mediaKind(), size, showKind = false) {
                Icon(
                    symbol = MaterialSymbols.PlayCircleOutline,
                    contentDescription = stringRes(id = Res.string.play),
                    modifier = Size50Modifier,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun VideoPlaceholder(
    content: MediaUrlContent,
    defaultModifier: Modifier,
) {
    if (content.blurhash != null || content.thumbhash != null) {
        DisplayBlurHash(
            content.blurhash,
            content.description,
            ContentScale.Crop,
            defaultModifier,
            thumbhash = content.thumbhash,
        )
    }
    Box(defaultModifier, contentAlignment = Alignment.Center) {
        Icon(
            symbol = MaterialSymbols.PlayCircleOutline,
            contentDescription = stringRes(id = Res.string.play),
            modifier = Size50Modifier,
            tint = if (content.blurhash != null || content.thumbhash != null) Color.White else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * The media of a profile gallery entry: each URL as a video or an image, with the entry's hash,
 * dimensions, blurhash and cover. Shared by the gallery grid's thumbnails and the entry's card in
 * the feed; [withHash] shows the verification banner, which a thumbnail leaves out.
 */
fun ProfileGalleryEntryEvent.toMediaContents(
    withHash: Boolean,
    uri: String?,
): List<MediaUrlContent> {
    val mimeType = mimeType()
    val description = summary() ?: content.ifBlank { null }
    val hash = if (withHash) hash() else null
    return urls().map { url ->
        if (RichTextParser.classifyMedia(url, mimeType) == MediaContentKind.VIDEO) {
            MediaUrlVideo(
                url = url,
                description = description,
                hash = hash,
                blurhash = blurhash(),
                dim = dimensions(),
                uri = uri,
                mimeType = mimeType,
                thumbhash = thumbhash(),
                artworkUri = image()?.imageUrl ?: thumb()?.imageUrl,
            )
        } else {
            MediaUrlImage(
                url = url,
                description = description,
                hash = hash,
                blurhash = blurhash(),
                dim = dimensions(),
                uri = uri,
                mimeType = mimeType,
                thumbhash = thumbhash(),
            )
        }
    }
}
