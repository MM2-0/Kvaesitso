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

import androidx.collection.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.HttpException
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.preview.UrlInfoItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copy_url_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.kind_comments
import com.vitorpamplona.amethyst.commons.resources.link_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.url_preview_open_in_browser
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.RichTextBlockSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.previewCardImageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlPreviewCard(
    url: String,
    previewInfo: UrlInfoItem,
    onUrlComments: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
) {
    val uri = LocalUriHandler.current
    val popupExpanded =
        remember {
            mutableStateOf(false)
        }

    if (popupExpanded.value) {
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        M3ActionDialog(
            title = stringRes(Res.string.link_actions_dialog_title),
            onDismiss = { popupExpanded.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.ContentCopy,
                    text = stringRes(Res.string.copy_url_to_clipboard),
                ) {
                    scope.launch {
                        clipboardManager.setText(url)
                        popupExpanded.value = false
                    }
                }
                onUrlComments?.let {
                    M3ActionRow(
                        icon = MaterialSymbols.Link,
                        text = stringRes(Res.string.kind_comments),
                    ) {
                        popupExpanded.value = false
                        it()
                    }
                }
            }
        }
    }

    // In a borderless post the card runs edge to edge like the post's media: square, framed only
    // above and below, with its text on the post text's edge.
    val bleed = LocalMediaBleed.current
    val borderColor = MaterialTheme.colorScheme.subtleBorder
    val frame =
        if (bleed == 0.dp) {
            remember(borderColor) {
                Modifier
                    .padding(vertical = RichTextBlockSpacing)
                    .fillMaxWidth()
                    .clip(QuoteBorder)
                    .border(1.dp, borderColor, QuoteBorder)
            }
        } else {
            Modifier
                .padding(vertical = RichTextBlockSpacing)
                .bleedHorizontally(bleed)
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    // Over the content: the card's image starts at its top edge and would hide the line.
                    val stroke = 1.dp.toPx()
                    drawLine(borderColor, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                    drawLine(borderColor, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                }
        }
    val textPadding = if (bleed == 0.dp) MaxWidthWithHorzPadding else Modifier.fillMaxWidth().padding(horizontal = bleed)
    val compactPadding = if (bleed == 0.dp) 10.dp else bleed

    val cardModifier =
        frame
            .combinedClickable(
                onClick = {
                    if (onCardClick != null) {
                        onCardClick()
                    } else {
                        runCatching { uri.openUri(url) }
                    }
                },
                onLongClick = {
                    popupExpanded.value = true
                },
            )

    // Only meaningful when the card's own tap does something else (e.g. opening the comment
    // thread); otherwise it would duplicate the card's open-in-browser tap.
    val hasCardClick = onCardClick != null
    val onOpenInBrowser: (() -> Unit)? =
        remember(hasCardClick, url, uri) {
            if (hasCardClick) {
                { runCatching { uri.openUri(url) } }
            } else {
                null
            }
        }

    // A page with no picture to lead with -- most often one with no OpenGraph at all, previewed
    // from its `<title>`, meta description and favicon -- gets the short, wide card. Painting the
    // big layout without its image would leave a text block that only looks broken.
    if (previewInfo.imageUrlFullPath.isBlank() && previewInfo.hasTextPreview) {
        CompactUrlPreviewCard(previewInfo, cardModifier, compactPadding, onOpenInBrowser)
    } else {
        LargeUrlPreviewCard(previewInfo, cardModifier, textPadding, onOpenInBrowser)
    }
}

@Composable
private fun LargeUrlPreviewCard(
    previewInfo: UrlInfoItem,
    modifier: Modifier,
    textPadding: Modifier,
    onOpenInBrowser: (() -> Unit)?,
) {
    Column(modifier = modifier) {
        // A Loaded preview no longer implies an image: a player page that ships no cover art is
        // kept (it has media to play), and painting its empty string left a blank 180dp box.
        if (previewInfo.imageUrlFullPath.isNotBlank()) {
            AsyncImage(
                model = previewInfo.imageUrlFullPath,
                contentDescription = previewInfo.title,
                contentScale = ContentScale.FillWidth,
                modifier = previewCardImageModifier,
                onState = rememberCaptureLoadingTracker(previewInfo.imageUrlFullPath),
            )
        }

        Row(
            modifier = textPadding,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = previewInfo.verifiedHost ?: previewInfo.url,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false),
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            onOpenInBrowser?.let { OpenInBrowserButton(it) }
        }

        Text(
            text = previewInfo.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = textPadding,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = previewInfo.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = textPadding,
            color = Color.Gray,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = DoubleVertSpacer)
    }
}

/**
 * The short, wide card: the site's icon on the left, its title, description and host on the
 * right. Built from what every page has even without OpenGraph -- what a browser tab shows.
 */
@Composable
private fun CompactUrlPreviewCard(
    previewInfo: UrlInfoItem,
    modifier: Modifier,
    horizontalPadding: Dp,
    onOpenInBrowser: (() -> Unit)?,
) {
    val host = previewInfo.verifiedHost ?: previewInfo.url

    Row(
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlPreviewIcon(previewInfo.iconUrlFullPath, host)

        Spacer(modifier = DoubleHorzSpacer)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = previewInfo.title.ifBlank { host },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (previewInfo.description.isNotBlank()) {
                Text(
                    text = previewInfo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // When there is no title the host already stands in for it on the first line.
            if (previewInfo.title.isNotBlank()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        onOpenInBrowser?.let { OpenInBrowserButton(it) }
    }
}

/**
 * Icon URLs the server refused (404 and the like) in this process. The image loader caches
 * successes but not failures, so without this a site with no `/favicon.ico` would be asked for it
 * again -- and 404 again -- every time its card scrolled back into view.
 */
private val failedIconUrls = LruCache<String, Unit>(200)

/**
 * The site's icon in a fixed square. A generic globe sits there until the icon actually loads,
 * so a site whose favicon is missing (the `/favicon.ico` guess is only a guess) or undecodable
 * still gets a tidy card instead of an empty hole.
 */
@Composable
private fun UrlPreviewIcon(
    iconUrl: String?,
    contentDescription: String,
) {
    Box(
        modifier =
            UrlPreviewIconModifier
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        var loaded by remember(iconUrl) { mutableStateOf(false) }

        if (!loaded) {
            Icon(
                symbol = MaterialSymbols.Language,
                contentDescription = null,
                modifier = Size24Modifier,
                tint = Color.Gray,
            )
        }

        if (iconUrl != null && failedIconUrls[iconUrl] == null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = UrlPreviewIconImageModifier,
                onSuccess = { loaded = true },
                // Only a server's answer is remembered. A network error (offline, a timeout) says
                // nothing about the icon and must be retried once the connection is back.
                onError = { if (it.result.throwable is HttpException) failedIconUrls.put(iconUrl, Unit) },
            )
        }
    }
}

/**
 * A site's favicon at text size, for one-line attributions. [fallback] shows until the icon
 * loads, and stays when it never does. Shares the refused-icon memory with the preview cards.
 */
@Composable
fun SiteIcon(
    iconUrl: String?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        var loaded by remember(iconUrl) { mutableStateOf(false) }

        if (!loaded) fallback()

        if (iconUrl != null && failedIconUrls[iconUrl] == null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
                onError = { if (it.result.throwable is HttpException) failedIconUrls.put(iconUrl, Unit) },
            )
        }
    }
}

@Composable
private fun OpenInBrowserButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.OpenInNew,
            contentDescription = stringRes(Res.string.url_preview_open_in_browser),
            modifier = Size14Modifier,
            tint = Color.Gray,
        )
    }
}

private val UrlPreviewIconModifier = Modifier.size(48.dp).clip(SmallBorder)
private val UrlPreviewIconImageModifier = Modifier.fillMaxSize().padding(6.dp)
