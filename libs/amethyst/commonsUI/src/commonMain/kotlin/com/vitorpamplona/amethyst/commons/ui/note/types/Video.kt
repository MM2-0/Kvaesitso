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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip71Video.selectVideoTrack
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip71Video.VideoEvent

@Composable
fun VideoDisplay(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val videoEvent = (note.event as? VideoEvent) ?: return
    val event = (videoEvent as? Event) ?: return

    val imeta = remember(videoEvent) { videoEvent.selectVideoTrack() } ?: return

    val title = remember(videoEvent) { videoEvent.title() }
    val summary = remember(videoEvent, title) { videoEvent.content.ifBlank { null }?.takeIf { title != it } }
    val image = remember(imeta) { imeta.image.firstOrNull() }
    val isYouTube = remember(imeta) { imeta.url.contains("youtube.com") || imeta.url.contains("youtu.be") }
    val tags = remember(note) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }
    val captions = rememberCaptionTracks(videoEvent, accountViewModel)

    val content: BaseMediaContent =
        // Keyed on the captions too: an addressable `text-track` resolves after the first frame,
        // and the player only picks up a track that is in the MediaItemData it was handed.
        remember(note, captions) {
            val description = videoEvent.content.ifBlank { null } ?: event.alt()
            // A NIP-71 event asserts its own type, so only an explicit image imeta diverts to the
            // viewer; an unclassifiable one still belongs in the player. See classifyMedia.
            val isImage = RichTextParser.classifyMedia(imeta.url, imeta.mimeType) == MediaContentKind.IMAGE
            val uri = note.toNostrUri()

            if (isImage) {
                MediaUrlImage(
                    url = imeta.url,
                    description = description,
                    hash = imeta.hash,
                    blurhash = imeta.blurhash,
                    dim = imeta.dimension,
                    uri = uri,
                    mimeType = imeta.mimeType,
                    thumbhash = imeta.thumbhash,
                )
            } else {
                MediaUrlVideo(
                    url = imeta.url,
                    description = description,
                    hash = imeta.hash,
                    dim = imeta.dimension,
                    uri = uri,
                    authorName = note.author?.toBestDisplayName(),
                    artworkUri = imeta.image.firstOrNull(),
                    mimeType = imeta.mimeType,
                    blurhash = imeta.blurhash,
                    thumbhash = imeta.thumbhash,
                    captions = captions,
                )
            }
        }

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = spacedBy(Size5dp),
        ) {
            if (isYouTube) {
                val uri = LocalUriHandler.current
                BleedingMedia { roundedCorner ->
                    Row(
                        modifier = Modifier.clickable { runCatching { uri.openUri(imeta.url) } },
                    ) {
                        image?.let {
                            MyAsyncImage(
                                imageUrl = it,
                                contentDescription =
                                    stringRes(
                                        Res.string.preview_card_image_for,
                                        it,
                                    ),
                                contentScale = ContentScale.FillWidth,
                                mainImageModifier = Modifier,
                                loadedImageModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier.fillMaxWidth(),
                                accountViewModel = accountViewModel,
                                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                                onError = { DefaultImageHeader(note, accountViewModel) },
                            )
                        } ?: run {
                            DefaultImageHeader(note, accountViewModel)
                        }
                    }
                }
            } else {
                BleedingMedia { roundedCorner ->
                    ZoomableContentView(
                        content = content,
                        roundedCorner = roundedCorner,
                        contentScale = contentScale,
                        accountViewModel = accountViewModel,
                    )
                }
            }

            title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }

            summary?.let {
                val callbackUri = remember(note) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                if (videoEvent.hasHashtags()) {
                    Row(
                        Modifier.fillMaxWidth(),
                    ) {
                        DisplayUncitedHashtags(videoEvent, summary, callbackUri, accountViewModel, nav)
                    }
                }
            }

            // Outside the summary block on purpose: divine.video shorts routinely credit a
            // soundtrack or a collaborator while leaving the description empty, and those credits
            // are the only attribution the reused work gets.
            RenderVideoCredits(videoEvent, accountViewModel, nav)
        }
    }
}
