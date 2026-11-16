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
package com.vitorpamplona.amethyst.commons.browser

/**
 * The powerful web features a site must ask for before using, as Chrome's site settings list them. Shared
 * by the keyless `:napplet` browser (which receives the page's request) and the main process (which
 * remembers the user's answer per origin), so both name them the same way over IPC.
 */
enum class BrowserSitePermission(
    /** Stable wire/storage name. Never rename: it is persisted. */
    val key: String,
) {
    CAMERA("camera"),
    MICROPHONE("microphone"),
    LOCATION("location"),
    ;

    /** The user's remembered answer for one permission on one origin. [ASK] = never answered. */
    enum class Decision { ASK, ALLOW, BLOCK }

    companion object {
        fun fromKey(key: String?): BrowserSitePermission? = entries.firstOrNull { it.key == key }
    }
}
