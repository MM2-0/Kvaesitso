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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogType

/** A JS dialog an embedded page opened, waiting for the user (the page's script is paused meanwhile). */
data class EmbeddedJsDialog(
    val id: Long,
    val type: PageDialogType,
    val url: String?,
    val message: String,
    val defaultValue: String,
    /** Offer "Block dialogs from this page" (from the page's second dialog on, as Chrome does). */
    val offerBlock: Boolean,
)

/** A camera / microphone / location request from an embedded page, waiting for an answer. */
data class EmbeddedPermissionRequest(
    val id: Long,
    val origin: String,
    val permissions: Set<BrowserSitePermission>,
)

/**
 * A download an embedded page started, waiting for consent before anything is fetched or written into
 * the shared Downloads collection. [fileName]/[sizeBytes] are the sanitized name and size (-1 when
 * unknown) the sandbox reports; [origin] is the WebView-reported origin, not a page field.
 */
data class EmbeddedDownloadRequest(
    val id: Long,
    val origin: String,
    val fileName: String,
    val sizeBytes: Long,
    val sourceHost: String?,
    val risky: Boolean,
)
