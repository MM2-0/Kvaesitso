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

/**
 * Which files a picker may hand to a web page's `<input type="file">`.
 *
 * The page reads what it's given with this app's own permissions, and the app can open its own
 * providers, exported or not. So a "gallery" that answers the chooser with a `content://` URI of one of
 * this app's providers, or a `file://` path into its private storage, would give the page the app's
 * private files (another site's cached blobs, image caches). Only `content://` URIs of other apps pass,
 * plus the camera captures this app created for the request. Chrome refuses the same.
 */
object NappletPickedFiles {
    fun mayHandToPage(
        scheme: String?,
        authority: String?,
        isOwnCapture: Boolean,
        isOwnAuthority: (String) -> Boolean,
    ): Boolean {
        if (isOwnCapture) return true
        if (!scheme.equals("content", ignoreCase = true)) return false
        val auth = authority?.takeIf { it.isNotBlank() } ?: return false
        // A URI may name several authorities (a;b): any of ours is ours.
        return auth.split(';').none { isOwnAuthority(it.trim()) }
    }
}
