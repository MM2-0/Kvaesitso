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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Loads, while on screen, the repository announcements needed to judge [item]'s statuses, PR
 * updates, labels and cover notes ([GitAuthority.announcementsToLoad]) through the event finder,
 * like any other note the screen shows. Each announcement that arrives names the next ones and
 * hints the repository's relays for them (`GitRepositoryEvent` is an address-hint provider), so the
 * set fills in as they load.
 */
@Composable
fun LoadGitRepositoryAnnouncements(item: Event) {
    LoadGitRepositoryAnnouncements(remember(item) { GitAuthority.repositoriesOf(item) })
}

/** [LoadGitRepositoryAnnouncements] for the repositories at [repositories]. */
@Composable
fun LoadGitRepositoryAnnouncements(repositories: List<Address>) {
    if (repositories.isEmpty()) return
    // Each announcement that arrives names the next ones to load.
    val announcements = repositories.map { it.dTag }.distinct().map { observeGitRepositoryAnnouncements(it) }
    val addresses = remember(repositories, announcements) { LocalCache.gitAuthority.announcementsToLoad(repositories) }
    addresses.forEach { address ->
        key(address) {
            val note = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
            EventFinderFilterAssemblerSubscription(note)
        }
    }
}
