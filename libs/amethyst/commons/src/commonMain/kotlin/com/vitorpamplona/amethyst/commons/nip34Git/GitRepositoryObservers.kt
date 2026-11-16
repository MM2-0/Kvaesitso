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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.RepositoryItemEssentialKinds
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.RepositoryItemKinds
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.repositoryCoordinates
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * The announcements published under [identifier], from the cache's own event observers: re-emitted
 * as one arrives, is replaced by a newer version or is withdrawn. Who is a member follows from
 * them, so what [com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority] decides about
 * the repository's items can change on each emission.
 */
fun EventCache.observeGitAnnouncements(identifier: String): Flow<List<GitRepositoryEvent>> = observeEvents(gitAnnouncementsFilter(identifier))

/**
 * The announcements published under [identifier] that the cache holds now: the list
 * [observeGitAnnouncements] emits first, read at once. A card seeds its state with it, so it
 * composes with its repository's roles on the first frame instead of filling them in a frame
 * later, while the flow's scan runs off the main thread. An indexed read of kind 30617 alone.
 */
fun EventCache.gitAnnouncements(identifier: String): List<GitRepositoryEvent> = filter(gitAnnouncementsFilter(identifier)).mapNotNull { it.event as? GitRepositoryEvent }

private fun gitAnnouncementsFilter(identifier: String) = Filter(kinds = listOf(GitRepositoryEvent.KIND), tags = mapOf("d" to listOf(identifier)))

/**
 * Emits whenever anything that decides the state of [identifier]'s items changes: an announcement,
 * an item filed under one of its coordinates, or a status, PR update, cover note or label of one of
 * those items. Lists and counts recompute on it; it carries no value of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun EventCache.observeGitRepositoryActivity(identifier: String): Flow<Unit> =
    // Every announcement change emits, even one that names no new coordinate: it can confirm or end
    // someone's role, and so change which statuses count.
    observeGitAnnouncements(identifier)
        .flatMapLatest { announcements ->
            val coordinates = announcements.flatMap(::repositoryCoordinates).distinct()
            if (coordinates.isEmpty()) return@flatMapLatest flowOf(Unit)
            observeEvents<Event>(Filter(kinds = RepositoryItemKinds, tags = mapOf("a" to coordinates)))
                .map { items -> items.map { it.id }.sorted() }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(Unit)
                    } else {
                        merge(
                            observeEvents<Event>(Filter(kinds = RepositoryItemEssentialKinds, tags = mapOf("e" to ids))),
                            observeEvents<Event>(Filter(kinds = listOf(GitPullRequestUpdateEvent.KIND), tags = mapOf("E" to ids))),
                        ).map { }
                    }
                }
        }
