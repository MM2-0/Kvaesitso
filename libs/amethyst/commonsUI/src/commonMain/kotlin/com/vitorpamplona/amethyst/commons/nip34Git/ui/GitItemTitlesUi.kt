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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The title [item] shows, its latest authorised rename applied ([GitItemTitles.titleOf]). */
@Composable
fun observeGitItemTitle(item: Note): String? {
    // Renames (1985 `#subject`) name the item with `e` only, so the repository's `#a` query never
    // brings them: ask the relays for the item's own replies and labels while it is on screen.
    EventFinderFilterAssemblerSubscription(item)
    // Renames arrive as the item's replies.
    val replies by remember(item) { item.flow().replies.stateFlow }.collectAsStateWithLifecycle()
    val announcements = observeGitRepositoryAnnouncements(item.event)
    return remember(replies, announcements) { GitItemTitles.titleOf(item, LocalCache.gitAuthority) }
}

/** The labels [item] shows: its own plus authorised NIP-32 `#t` labels ([GitItemTitles.labelsOf]). */
@Composable
fun observeGitItemLabels(item: Note): ImmutableList<String> {
    // Same as [observeGitItemTitle]: `#t` labels reach the item only through its own `e` fetch.
    EventFinderFilterAssemblerSubscription(item)
    val labels by remember(item) { item.flow().labels.stateFlow }.collectAsStateWithLifecycle()
    val announcements = observeGitRepositoryAnnouncements(item.event)
    return remember(labels, announcements) { GitItemTitles.labelsOf(item, LocalCache.gitAuthority).toImmutableList() }
}
