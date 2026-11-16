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
package com.vitorpamplona.amethyst.commons.relayClient.softwareapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription

/** Keeps the open app page's releases loading from relays while the page is on screen. */
@Composable
fun SoftwareReleasesFilterAssemblerSubscription(
    app: AddressableNote,
    filterAssembler: SoftwareReleasesFilterAssembler,
) {
    val state = remember(app) { SoftwareReleasesQueryState(app) }
    LifecycleAwareKeyDataSourceSubscription(state, filterAssembler)

    // The filter is built from the app event (its id, publisher and credited maintainers), so a page
    // opened from a bare link has nothing to ask for until that event lands. Re-assemble when it
    // does, and when a newer version of the app replaces it.
    val noteState by remember(app) { app.flow().metadata.stateFlow }.collectAsStateWithLifecycle()
    val appEventId = noteState.note.event?.id
    LaunchedEffect(appEventId) {
        if (appEventId != null) filterAssembler.invalidateFilters()
    }
}
