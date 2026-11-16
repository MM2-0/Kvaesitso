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

import android.net.Uri
import android.webkit.ValueCallback

/**
 * Holds the one in-flight `onShowFileChooser` callback for a WebView and guarantees it is invoked
 * exactly once.
 *
 * This matters more than it looks. WebView treats a file input as busy until its `filePathCallback`
 * fires, so a callback that is simply dropped — the user backed out of the picker, the session was
 * torn down, no app could handle the Intent — leaves that `<input>` permanently dead for the life of
 * the page: every later tap on it is ignored, with nothing logged. Cancelling with `null` is what
 * releases it, so every exit path here ends in a delivery.
 *
 * [start] returns a request id. The embedded hosts round-trip it through the main process and hand it
 * back to [deliver], so a result that outlived its request (a second tap while the picker was already
 * up, a session rebuilt underneath) can be recognised and dropped instead of being fed to whichever
 * input happens to be waiting now. Main-thread only, like the WebView callbacks it serves.
 */
class PendingFileChooser {
    private var requestId = 0L
    private var callback: ValueCallback<Array<Uri>>? = null

    /**
     * Registers [newCallback] as the pending request and returns its id. Any request still in flight is
     * cancelled first — the page asked for a new pick, so the old input must be released rather than
     * left waiting for a result that will never be routed to it.
     */
    fun start(newCallback: ValueCallback<Array<Uri>>): Long {
        cancel()
        requestId += 1
        callback = newCallback
        return requestId
    }

    /** Delivers [uris] (null = the user cancelled) to the pending request, if there still is one. */
    fun deliver(uris: Array<Uri>?) {
        val pending = callback ?: return
        callback = null
        pending.onReceiveValue(uris)
    }

    /** Delivers [uris] only if [id] is still the current request; a late or stale result is dropped. */
    fun deliver(
        id: Long,
        uris: Array<Uri>?,
    ) {
        if (id != requestId) return
        deliver(uris)
    }

    /** Releases the page's file input without a file. Safe to call when nothing is pending. */
    fun cancel() = deliver(null)
}
