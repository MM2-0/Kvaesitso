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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_kind_audio
import com.vitorpamplona.amethyst.commons.resources.media_kind_image
import com.vitorpamplona.amethyst.commons.resources.media_kind_pdf
import com.vitorpamplona.amethyst.commons.resources.media_kind_video
import com.vitorpamplona.amethyst.commons.resources.media_type_and_size
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.MediaSizeCache
import com.vitorpamplona.amethyst.commons.service.MediaSizeRole
import com.vitorpamplona.amethyst.commons.service.resolveMediaSize
import com.vitorpamplona.amethyst.commons.ui.cartridges.CartSizeLabel
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size50dp
import com.vitorpamplona.amethyst.commons.util.formatFileSize
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What a held-back file is, for its size label's type word and the client that asks for its size. */
enum class MediaKind(
    val role: MediaSizeRole,
) {
    IMAGE(MediaSizeRole.IMAGE),
    VIDEO(MediaSizeRole.VIDEO),
    AUDIO(MediaSizeRole.VIDEO),
    PDF(MediaSizeRole.PREVIEW),
}

/** Audio or video, for a file the player handles: the one rule every player placeholder uses. */
fun mediaKindOf(
    mimeType: String?,
    url: String,
): MediaKind = if (RichTextParser.isAudioContent(mimeType, url)) MediaKind.AUDIO else MediaKind.VIDEO

fun MediaUrlContent.mediaKind(): MediaKind =
    when (this) {
        is MediaUrlImage -> MediaKind.IMAGE
        is MediaUrlPdf -> MediaKind.PDF
        is MediaUrlVideo -> mediaKindOf(mimeType, url)
        else -> MediaKind.IMAGE
    }

@Composable
private fun MediaKind.word(): String =
    stringRes(
        when (this) {
            MediaKind.IMAGE -> Res.string.media_kind_image
            MediaKind.VIDEO -> Res.string.media_kind_video
            MediaKind.AUDIO -> Res.string.media_kind_audio
            MediaKind.PDF -> Res.string.media_kind_pdf
        },
    )

/**
 * The size of a file the user hasn't loaded: the one the event declares, else one learned without
 * downloading it (see [resolveMediaSize]). Null until known, and for good when the host won't say.
 * Only a [held] file is asked about: pass false where the file loads anyway, and the declared size,
 * if any, is all it gets.
 */
@Composable
fun rememberMediaSize(
    url: String,
    kind: MediaKind,
    accountViewModel: AccountViewModel,
    mimeType: String? = null,
    declaredSize: Long? = null,
    held: Boolean = true,
): Long? {
    if (declaredSize != null || !held) return declaredSize
    // Keyed by the URL so a recycled feed slot never shows the previous file's size for a frame.
    return key(url) {
        // Only this URL's entry: an answer for another file doesn't recompose this placeholder.
        val size by remember(url) {
            MediaSizeCache.entries.map { it[url]?.takeIf { bytes -> bytes > 0 } }.distinctUntilChanged()
        }.collectAsState(MediaSizeCache.get(url))
        LaunchedEffect(url, mimeType, kind) { accountViewModel.httpClientBuilder.resolveMediaSize(url, mimeType, kind.role) }
        size
    }
}

@Composable
fun rememberMediaSize(
    content: MediaUrlContent,
    accountViewModel: AccountViewModel,
    held: Boolean = true,
): Long? = rememberMediaSize(content.url, content.mediaKind(), accountViewModel, content.mimeType, content.sizeBytes, held)

/**
 * "Video · 12.4 MB", or "Video" until the size is known; with [showKind] off, the size alone. The size
 * fades in when it arrives. [pill] draws it on a dark translucent pill, for use over a blurhash; without
 * it the text takes the surrounding content color.
 */
@Composable
fun MediaSizeLabel(
    kind: MediaKind,
    sizeBytes: Long?,
    modifier: Modifier = Modifier,
    showKind: Boolean = true,
    pill: Boolean = false,
) {
    val word = kind.word()
    AnimatedContent(
        targetState = sizeBytes,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
    ) { size ->
        val text =
            when {
                size == null -> if (showKind) word else null
                showKind -> stringRes(Res.string.media_type_and_size, word, formatFileSize(size))
                else -> formatFileSize(size)
            }
        if (text != null) {
            Text(
                text = text,
                color = if (pill) Color.White else Color.Unspecified,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    if (pill) {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    } else {
                        Modifier
                    },
            )
        }
    }
}

/** A placeholder's load [icon] with the file's size in a pill under it (mockup A). */
@Composable
fun IconWithSizeLabel(
    kind: MediaKind,
    sizeBytes: Long?,
    showKind: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        MediaSizeLabel(kind, sizeBytes, showKind = showKind, pill = true)
    }
}

/**
 * A held-back file with no blurhash but a known shape: a plain card at that shape with the download
 * icon, "Video · 12.4 MB" and the host. Tapping anywhere loads it. With no [ratio] it fills the space
 * its parent gives it, as a grid cell does. [underCartPlay] leaves the middle to a game cartridge's
 * Play button and shows only the size under it.
 */
@Composable
fun MediaDownloadCard(
    url: String,
    kind: MediaKind,
    sizeBytes: Long?,
    ratio: Float?,
    modifier: Modifier,
    underCartPlay: Boolean = false,
    onLoad: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onLoad),
        contentAlignment = Alignment.Center,
    ) {
        if (underCartPlay) {
            CartSizeLabel(sizeBytes)
        } else {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    DownloadForOfflineIcon(Size50dp, MaterialTheme.colorScheme.onSurfaceVariant)
                    MediaSizeLabel(kind, sizeBytes)
                    HostText(url)
                }
            }
        }
    }
}

/**
 * A held-back file with neither a blurhash nor a known shape: a compact row with the download icon,
 * "Image · 2.3 MB" and the host, in place of the bare link. Tapping it loads the file.
 */
@Composable
fun MediaDownloadRow(
    url: String,
    kind: MediaKind,
    sizeBytes: Long?,
    modifier: Modifier,
    onLoad: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onLoad)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DownloadForOfflineIcon(Size35dp)
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column {
                MediaSizeLabel(kind, sizeBytes)
                HostText(url)
            }
        }
    }
}

@Composable
private fun HostText(url: String) {
    val host = remember(url) { OmniboxInput.hostOf(url) ?: url }
    Text(
        text = host,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
