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
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.rememberBlossomUriOpener
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl as SharedClickableUrl

/**
 * A clickable URL. Plain http(s) links delegate to the shared cross-platform
 * [SharedClickableUrl] (opens via `LocalUriHandler`); only the Android-specific
 * `blossom:` case — opening a Blossom media URI in an external app via an Intent —
 * stays here. Touch front end, so no underline.
 */
@Composable
fun ClickableUrlOrBlossom(
    urlText: String,
    url: String,
    style: TextStyle = LocalTextStyle.current,
    onError: (StringResource, StringResource) -> Unit = { _, _ -> },
) {
    if (url.startsWith("blossom:")) {
        val blossomOpener = rememberBlossomUriOpener()
        ClickableTextPrimary(
            text = urlText,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            onClick = { blossomOpener.open(url, onError) },
        )
    } else {
        SharedClickableUrl(url = url, displayText = urlText, style = style)
    }
}
