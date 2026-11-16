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
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.amethyst.commons.nip34Git.gitAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.observeGitAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.observeGitRepositoryActivity
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.runningFold

/**
 * The announcements of the repository [item] (an issue, patch or PR) is filed under, from the
 * cache's event observers: key what [LocalCache.gitAuthority] decides on it, so a membership that
 * arrives later is applied. Null for an event that names no repository.
 */
@Composable
fun observeGitRepositoryAnnouncements(item: Event?): List<GitRepositoryEvent>? {
    val identifier = remember(item) { item?.let { GitAuthority.repositoriesOf(it).firstOrNull()?.dTag } } ?: return null
    return observeGitRepositoryAnnouncements(identifier)
}

/** [observeGitRepositoryAnnouncements] for the repositories published under [identifier]. */
@Composable
fun observeGitRepositoryAnnouncements(identifier: String): List<GitRepositoryEvent> {
    // Seeded with what the cache holds now, so the first frame already has it: the flow only
    // brings what changes after.
    val initial = remember(identifier) { LocalCache.gitAnnouncements(identifier) }
    val announcements by remember(identifier) { LocalCache.observeGitAnnouncements(identifier) }.collectAsStateWithLifecycle(initial)
    return announcements
}

/**
 * Counts the changes to anything that decides the state of [identifier]'s items (an announcement,
 * an item, or a status, update, cover note or label of one): key what [GitStatuses] and friends
 * compute over the repository's items with it.
 */
@Composable
fun observeGitRepositoryActivity(identifier: String): Long {
    val changes by remember(identifier) { LocalCache.observeGitRepositoryActivity(identifier).runningFold(0L) { count, _ -> count + 1 } }.collectAsStateWithLifecycle(0L)
    return changes
}

/** Whether [change] (a status or a PR update) counts for [item]; see [com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority.isAuthorisedChange]. */
@Composable
fun rememberIsAuthorisedChange(
    change: Event,
    item: Event?,
): Boolean {
    val announcements = observeGitRepositoryAnnouncements(item)
    return remember(change, item, announcements) { LocalCache.gitAuthority.isAuthorisedChange(change, item) }
}

/** The status [item] shows, recomputed when a status arrives or its repository's members change. */
@Composable
fun observeGitStatus(item: Note): GitStatusEvent? {
    val replies by remember(item) { item.flow().replies.stateFlow }.collectAsStateWithLifecycle()
    val announcements = observeGitRepositoryAnnouncements(item.event)
    return remember(replies, announcements) { GitStatuses.latest(item, LocalCache.gitAuthority) }
}

/** [pullRequest]'s updates that count, oldest first. */
@Composable
fun observeGitPullRequestUpdates(pullRequest: Note): ImmutableList<GitPullRequestUpdateEvent> {
    val replies by remember(pullRequest) { pullRequest.flow().replies.stateFlow }.collectAsStateWithLifecycle()
    val announcements = observeGitRepositoryAnnouncements(pullRequest.event)
    return remember(replies, announcements) { GitPullRequestRevisions.updates(pullRequest, LocalCache.gitAuthority).toImmutableList() }
}

/** [item]'s cover notes that count, oldest first: the versions its summary steps through. */
@Composable
fun observeGitCoverNotes(item: Note): ImmutableList<GitCoverNoteEvent> {
    val replies by remember(item) { item.flow().replies.stateFlow }.collectAsStateWithLifecycle()
    val announcements = observeGitRepositoryAnnouncements(item.event)
    return remember(replies, announcements) { GitCoverNotes.versions(item, LocalCache.gitAuthority).toImmutableList() }
}
