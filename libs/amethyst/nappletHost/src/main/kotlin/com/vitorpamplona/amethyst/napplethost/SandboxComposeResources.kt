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
package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import com.vitorpamplona.quartz.utils.Log

/**
 * Gives this process the Context that Compose Resources needs.
 *
 * The browser chrome is drawn by shared composables that read `Res.string`, and
 * they run here, in `:napplet`. Compose Resources learns its Context from a
 * ContentProvider the library declares, and a provider is only instantiated in
 * the process that owns it — so every string lookup in the sandbox died with
 * MissingResourceException, taking the window with it.
 *
 * `android:multiprocess="true"` (set on that provider in the app manifest) lets
 * each process hold its own instance, but Android creates it lazily, on first
 * access. Nothing in `:napplet` ever addresses the provider by authority, so
 * without this it is never created. Acquiring a client once is that first
 * access; the provider's `onCreate` then records this process's Context and
 * every later lookup resolves locally, with no IPC.
 *
 * Call before anything composes. It is cheap and idempotent.
 */
object SandboxComposeResources {
    private var done = false

    fun ensure(context: Context) {
        if (done) return
        done = true
        val authority = "${context.packageName}.resources.AndroidContextProvider"
        runCatching {
            context.contentResolver.acquireContentProviderClient(authority)?.close()
        }.onFailure {
            // Not fatal on its own: the failure surfaces later as a missing
            // string, which is easier to read with this line above it.
            Log.w("SandboxComposeResources", "could not warm $authority: ${it.message}")
        }
    }
}
