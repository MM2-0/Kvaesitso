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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashGridBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.commons.ui.components.collectContentWarningReasons
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video.UserCardHeader
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PictureCardCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? PictureEvent) ?: return
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Author header row
        UserCardHeader(baseNote, accountViewModel, nav)

        // Image content
        PictureCardImage(baseNote, event, backgroundColor, accountViewModel)

        // Title and content
        PictureCardCaption(event)

        // Reactions row
        ReactionsRow(
            baseNote = baseNote,
            showReactionDetail = true,
            addPadding = true,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
            alignToText = true,
        )
    }
}

@Composable
private fun PictureCardImage(
    note: Note,
    event: PictureEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
) {
    val uri = remember(note) { note.toNostrUri() }
    val isSensitive = remember(note) { event.isSensitiveOrNSFW() }
    val reasons = remember(note) { collectContentWarningReasons(event) }

    val images by
        remember(note) {
            mutableStateOf(
                event
                    .imetaTags()
                    .map {
                        MediaUrlImage(
                            url = it.url,
                            description = it.alt,
                            hash = it.hash,
                            blurhash = it.blurhash,
                            dim = it.dimension,
                            uri = uri,
                            mimeType = it.mimeType,
                            thumbhash = it.thumbhash,
                        )
                    }.toImmutableList(),
            )
        }

    if (images.isEmpty()) return

    if (images.size == 1) {
        val single = images.first()
        val ratio = single.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(single.url)
        ContentWarningGate(
            isSensitive = isSensitive,
            reasons = reasons,
            preloadUrls = listOf(single.url),
            accountViewModel = accountViewModel,
            modifier = mediaSizingModifier(ratio, ContentScale.FillWidth),
            backdrop = (single.thumbhash ?: single.blurhash)?.let { { BlurhashBackdrop(single.blurhash, single.description, single.thumbhash) } },
        ) {
            ZoomableContentView(
                content = single,
                images = images,
                roundedCorner = false,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        }
    } else {
        ContentWarningGate(
            isSensitive = isSensitive,
            reasons = reasons,
            preloadUrls = images.map { it.url },
            accountViewModel = accountViewModel,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            backdrop = { BlurhashGridBackdrop(images) },
        ) {
            AutoNonlazyGrid(images.size) {
                ZoomableContentView(
                    content = images[it],
                    images = images,
                    roundedCorner = false,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
fun PictureCardCaption(event: PictureEvent) {
    val title = event.title()
    val content = event.content

    if (title != null || content.isNotBlank()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
