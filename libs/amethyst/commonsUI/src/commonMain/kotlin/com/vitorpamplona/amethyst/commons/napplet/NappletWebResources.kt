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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The bytes of [NappletWebContract]'s trusted shell page and `window.napplet` shim, which live in
 * `commonMain/composeResources/files/napplet/` and are read through the generated [Res] accessor.
 */
object NappletWebResources {
    const val SHELL_HTML_PATH = "files/napplet/shell.html"
    const val SHIM_JS_PATH = "files/napplet/shim.js"

    /**
     * Asset directory the compose-resources of `:commonsUI` are packaged under on Android (mirrors the
     * prefix the generated [Res] accessor prepends). Hosts that run in a process without an
     * initialized compose-resources context — e.g. the isolated `:napplet` process, which skips app
     * init to stay key-free — read [SHELL_HTML_PATH]/[SHIM_JS_PATH] straight from `assets` under this
     * root instead of going through [shellHtml]/[shimJs].
     */
    const val RESOURCE_ASSET_ROOT = "composeResources/com.vitorpamplona.amethyst.commons.resources/"

    /** The trusted shell page's HTML, read from the shared bundle. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shellHtml(): ByteArray = Res.readBytes(SHELL_HTML_PATH)

    /** The `window.napplet` client shim a host injects into the applet document. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun shimJs(): ByteArray = Res.readBytes(SHIM_JS_PATH)
}
