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

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink

@Composable
fun ClickableTextPrimary(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
) {
    ClickableTextColor(
        text,
        modifier,
        style,
        softWrap,
        overflow,
        maxLines,
        MaterialTheme.colorScheme.primary,
        onClick,
    )
}

@Composable
fun ClickableTextColor(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    // onClick is baked into the LinkAnnotation, so it must be a key: a recycled
    // slot with the same label and a new handler would otherwise keep firing
    // the old target.
    Text(
        text =
            remember(text, linkColor, onClick) {
                buildAnnotatedString {
                    appendLink(text, linkColor, onClick)
                }
            },
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
    )
}

@Composable
fun ClickableTextNormal(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
) {
    Text(
        text =
            remember(text, onClick) {
                buildAnnotatedString {
                    appendLink(text, onClick)
                }
            },
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
    )
}

inline fun Builder.appendLink(
    text: String,
    color: Color,
    crossinline onClick: () -> Unit,
) = withLink(
    LinkAnnotation.Clickable(
        "clickable",
        TextLinkStyles(SpanStyle(color)),
    ) {
        onClick()
    },
) {
    append(text)
}

inline fun Builder.appendLink(
    text: String,
    crossinline onClick: () -> Unit,
) = withLink(
    LinkAnnotation.Clickable("clickable") {
        onClick()
    },
) {
    append(text)
}

inline fun buildLinkString(
    text: String,
    crossinline onClick: () -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        withLink(
            LinkAnnotation.Clickable("link") {
                onClick()
            },
        ) {
            append(text)
        }
    }
