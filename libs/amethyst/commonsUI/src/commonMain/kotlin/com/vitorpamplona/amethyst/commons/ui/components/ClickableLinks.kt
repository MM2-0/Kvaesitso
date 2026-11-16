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

import androidx.compose.foundation.clickable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/**
 * A primary-colored, single-line clickable URL that opens via the platform
 * [LocalUriHandler] — the browser on Android, `Desktop.browse` on JVM — so the
 * "open a link" behavior is identical on every front end without a seam.
 *
 * [underline] + the hover cursor are mouse-first affordances: Desktop passes
 * `true`; touch front ends leave it `false`. A scheme-less [url] is opened as
 * `https://`; a `tel:` or `mailto:` one goes to the platform as it is, which
 * hands it to the dialer or the mail app.
 */
@Composable
fun ClickableUrl(
    url: String,
    displayText: String = url,
    modifier: Modifier = Modifier,
    underline: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = displayText,
        style = style,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = if (underline) TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        modifier =
            modifier.pointerHoverIcon(PointerIcon.Hand).clickable {
                runCatching {
                    uriHandler.openUri(if (url.contains("://") || isCallOrMail(url)) url else "https://$url")
                }
            },
    )
}

private fun isCallOrMail(url: String) = url.startsWith("tel:", ignoreCase = true) || url.startsWith("mailto:", ignoreCase = true)

/**
 * A primary-colored clickable email that opens the platform mail client via
 * [LocalUriHandler] (`mailto:`). Strips a leading `mailto:` from the display text.
 * [underline] + hover cursor are the Desktop mouse-first affordances.
 */
@Composable
fun ClickableEmail(
    address: String,
    modifier: Modifier = Modifier,
    underline: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val display = remember(address) { address.removePrefix("mailto:") }
    Text(
        text = display,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = if (underline) TextDecoration.Underline else null,
        modifier =
            modifier.pointerHoverIcon(PointerIcon.Hand).clickable {
                // '%' is legal in an address but starts an escape in a mailto: URI (RFC 6068).
                runCatching { uriHandler.openUri("mailto:" + display.replace("%", "%25")) }
            },
    )
}

/**
 * A primary-colored clickable phone number that opens the platform dialer via
 * [LocalUriHandler] (`tel:`). Strips a leading `tel:` from the display text.
 * [underline] + hover cursor are the Desktop mouse-first affordances.
 */
@Composable
fun ClickablePhone(
    number: String,
    modifier: Modifier = Modifier,
    underline: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val display = remember(number) { number.removePrefix("tel:") }
    Text(
        text = display,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = if (underline) TextDecoration.Underline else null,
        modifier =
            modifier.pointerHoverIcon(PointerIcon.Hand).clickable {
                runCatching { uriHandler.openUri("tel:$display") }
            },
    )
}
