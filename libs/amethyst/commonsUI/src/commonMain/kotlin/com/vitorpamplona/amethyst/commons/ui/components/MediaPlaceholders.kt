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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_placeholder_loading_content
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.service.image.placeholderModel
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.rememberBlossomUriOpener
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource

@Composable
fun ImageUrlWithDownloadButton(
    url: String,
    showImage: MutableState<Boolean>,
) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground

    val regularText = remember { SpanStyle(color = background) }
    val clickableTextStyle = remember { SpanStyle(color = primary) }

    val annotatedTermsString =
        remember {
            buildAnnotatedString {
                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    append("$url ")
                    pop()
                }

                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val pressIndicator =
        remember {
            Modifier
                .fillMaxWidth()
                .clickable { runCatching { uri.openUri(url) } }
        }

    Row(
        modifier =
            Modifier
                .width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier =
                pressIndicator
                    .weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        InlineDownloadIcon(showImage)
    }
}

@Composable
private fun InlineDownloadIcon(showImage: MutableState<Boolean>) =
    IconButton(
        modifier = Modifier.size(Size20dp),
        onClick = { showImage.value = true },
    ) {
        DownloadForOfflineIcon(Size24dp)
    }

@Composable
fun WaitAndDisplay(content: @Composable (AnimatedVisibilityScope.() -> Unit)) {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        visible.value = true
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(),
        exit = fadeOut(),
        content = content,
    )
}

@Composable
fun DisplayUrlWithLoadingSymbol(
    content: BaseMediaContent,
    onError: (StringResource, StringResource) -> Unit = { _, _ -> },
) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground

    val regularText = remember { SpanStyle(color = background) }
    val clickableTextStyle = remember { SpanStyle(color = primary) }

    val blossomOpener = rememberBlossomUriOpener()
    val loadingText = stringRes(Res.string.media_placeholder_loading_content)

    val annotatedTermsString =
        remember(loadingText) {
            buildAnnotatedString {
                if (content is MediaUrlContent) {
                    withStyle(clickableTextStyle) {
                        pushStringAnnotation("routeToImage", "")
                        append(content.url + " ")
                        pop()
                    }
                } else {
                    withStyle(regularText) { append(loadingText) }
                }

                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val pressIndicator =
        remember {
            if (content is MediaUrlContent) {
                Modifier.clickable {
                    if (content.url.startsWith("blossom:")) {
                        blossomOpener.open(content.url, onError)
                    } else {
                        runCatching { uri.openUri(content.url) }
                    }
                }
            } else {
                Modifier
            }
        }

    Row(
        modifier =
            Modifier
                .width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier = pressIndicator.weight(1f, fill = false),
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
        )
        InlineLoadingIcon()
    }
}

@Composable
fun DisplayUrlWithLoadingSymbol(
    url: String,
    onError: (StringResource, StringResource) -> Unit = { _, _ -> },
) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val annotatedTermsString =
        remember {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) {
                    pushStringAnnotation("routeToImage", "")
                    append("$url ")
                    pop()
                }
            }
        }

    val blossomOpener = rememberBlossomUriOpener()

    val pressIndicator =
        remember {
            Modifier.clickable {
                if (url.startsWith("blossom:")) {
                    blossomOpener.open(url, onError)
                } else {
                    runCatching {
                        uri.openUri(url)
                    }
                }
            }
        }

    Row(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier = pressIndicator.weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        InlineLoadingIcon()
    }
}

@Composable
private fun InlineLoadingIcon() = LoadingAnimation()

@Composable
fun DisplayBlurHash(
    blurhash: String?,
    description: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    thumbhash: String? = null,
) {
    val model =
        placeholderModel(thumbhash, blurhash) ?: return

    AsyncImage(
        model = model,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
    )
}
