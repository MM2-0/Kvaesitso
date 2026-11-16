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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.no_blossom_apps_found_description
import com.vitorpamplona.amethyst.commons.resources.no_blossom_apps_found_title
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.cancellation.CancellationException

/** Opens a `blossom:` URI in whichever installed app handles it. */
fun interface BlossomUriOpener {
    /** Opens [blossomUri], or reports through [onError] that no app can. */
    fun open(
        blossomUri: String,
        onError: (StringResource, StringResource) -> Unit,
    )
}

/** A [BlossomUriOpener] over the platform's URI handler (a `VIEW` intent on Android). */
@Composable
fun rememberBlossomUriOpener(): BlossomUriOpener {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) {
        BlossomUriOpener { blossomUri, onError ->
            try {
                uriHandler.openUri(blossomUri)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError(Res.string.no_blossom_apps_found_title, Res.string.no_blossom_apps_found_description)
            }
        }
    }
}
