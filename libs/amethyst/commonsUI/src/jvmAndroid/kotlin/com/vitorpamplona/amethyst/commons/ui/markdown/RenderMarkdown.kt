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
package com.vitorpamplona.amethyst.commons.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText

private val ALLOWED_SCHEMES = setOf("https", "http", "nostr", "lightning", "highlight")

/**
 * Escapes markdown special characters inside highlighted text so it doesn't
 * break the markdown parser when wrapped in a link.
 */
private fun escapeMarkdownInLink(text: String): String =
    text
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")

@Composable
fun RenderMarkdown(
    content: String,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f,
    highlightedTexts: List<String> = emptyList(),
) {
    val processedContent =
        remember(content, highlightedTexts) {
            if (highlightedTexts.isEmpty()) {
                content
            } else {
                var result = content
                highlightedTexts.sortedByDescending { it.length }.forEachIndexed { index, text ->
                    val idx = result.indexOf(text)
                    if (idx >= 0) {
                        val escaped = escapeMarkdownInLink(text)
                        result = result.replaceFirst(text, "[$escaped](highlight://$index)")
                    }
                }
                result
            }
        }

    val astNode =
        remember(processedContent) {
            CommonmarkAstNodeParser(AmethystMarkdownParseOptions).parse(processedContent)
        }

    val uriHandler =
        remember(onLinkClick) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val scheme = uri.substringBefore(":").lowercase()
                    if (scheme in ALLOWED_SCHEMES) {
                        onLinkClick(uri)
                    }
                }
            }
        }

    val currentDensity = LocalDensity.current
    val scaledDensity =
        remember(fontScale, currentDensity) {
            if (fontScale == 1.0f) {
                currentDensity
            } else {
                Density(
                    density = currentDensity.density * fontScale,
                    fontScale = currentDensity.fontScale,
                )
            }
        }

    CompositionLocalProvider(
        LocalUriHandler provides uriHandler,
        LocalDensity provides scaledDensity,
    ) {
        RichText(
            modifier = modifier,
            style = RichTextStyle(),
        ) {
            BasicMarkdown(astNode)
        }
    }
}
