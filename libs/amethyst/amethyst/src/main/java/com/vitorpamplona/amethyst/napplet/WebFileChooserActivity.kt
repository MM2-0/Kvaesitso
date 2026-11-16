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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.napplethost.WebFileChooserLauncher

/**
 * Invisible main-process host for one file pick made on behalf of an **embedded** WebView surface
 * ([WebFileChooserCoordinator]). The surface renders from the keyless `:napplet` process, which has no
 * Activity to start a picker or a permission prompt from. The page's `<input type="file">` stays busy
 * until it hears back, which [PickerHostActivity] guarantees on every exit.
 */
class WebFileChooserActivity : PickerHostActivity<WebFileChooserCoordinator.Ask, Array<String>>(WebFileChooserCoordinator.handoff) {
    // Field, not a local: registerForActivityResult must run before this activity reaches STARTED.
    private val chooser =
        WebFileChooserLauncher(this) { uris ->
            report(uris?.map { it.toString() }?.toTypedArray())
            finish()
        }

    override fun launch(ask: WebFileChooserCoordinator.Ask) {
        chooser.launch(
            acceptTypes = ask.acceptTypes,
            allowMultiple = ask.allowMultiple,
            captureEnabled = ask.captureEnabled,
            pageTitle = ask.pageTitle,
        )
    }

    /**
     * The request's scratch files and camera grants go with it: without this a host killed under the
     * picker would leave them for the stale sweep to find a day later.
     */
    override fun onDestroy() {
        chooser.teardown()
        super.onDestroy()
    }
}
