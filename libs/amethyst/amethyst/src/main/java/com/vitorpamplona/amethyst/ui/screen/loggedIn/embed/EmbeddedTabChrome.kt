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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import com.vitorpamplona.amethyst.commons.browser.ui.pill.AddressSuggestion
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi

/**
 * What a running app surface shows in its top pull-down pill, as plain data so [EmbeddedTabLayer] can draw
 * the shared [com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPill] over the (z-below) surface
 * for the active tab. Deliberately not a corner pill: that's where a site usually puts the user's own
 * avatar/menu, so the handle lives at the top-center instead.
 *
 * *Which* actions show comes from [BrowserPillUi.chrome] through
 * [com.vitorpamplona.amethyst.commons.browser.BrowserChrome] — the same layout the full-screen windows
 * use. Everything the user picks arrives in [onEvent]; find in page and the console are handled by the
 * layer itself, since it draws them.
 */
data class EmbeddedTabChrome(
    val ui: BrowserPillUi,
    val onEvent: (BrowserPillEvent) -> Unit,
    /** Address-editor suggestions for what the user has typed. */
    val suggestionsFor: (String) -> List<AddressSuggestion> = { emptyList() },
)
